#!/usr/bin/env python3

import subprocess
import glob
import re
import argparse
import os
import yaml
import time
from multiprocessing import Pool

# CI script, as it has problems with benchexec due to c groups
cpa_bin = ["./scripts/cpa.sh"]

def print_ret(ret):
    if ret:
        print(" ".join(ret.args))
        print(ret.stdout)
        print(ret.stderr)

def fail_if_incorrect_verdict(ret, expected, real):
    def fail():
        print_ret(ret)
        raise ValueError("Expected verdict {} and obtain {}".format(expected, real))

    if expected is None:
        # No expected verdict
        return
    if type(expected) is list:
        # Several possible verdicts, like flacky false-timeout
        for exp in expected:
            if exp == real:
                return
        fail()
    else:
        if expected != real:
            fail()


def parse_verdict(ret):
    m = re.search('Verification result: (.*)\. ', ret.stdout)
    if m:
        verdict = m.group(1)
        return verdict

    match_verdicts = {'Shutdown requested': 'TIMEOUT',
                      'Analysis interrupted': 'TIMEOUT',
                      'Exception in thread': 'EXCEPTION',
                      'Error: ': 'ERROR'}

    for m, v in match_verdicts.items():
        if re.search(m, ret.stderr):
            return v

    print_ret(ret)
    raise ValueError("No verification result")


def call_cpachecker(options, program, expected, timeout=900):
    try:
        print("  Run CPALockator for {}".format(program))
        cmd_line = cpa_bin + options + [program]

        tic = time.perf_counter()
        ret = subprocess.run(cmd_line, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
        toc = time.perf_counter()
        print("  Elapsed time: {:0.2f}s".format(toc - tic))

        if ret.returncode != 0 and not expected:
            # If there is no expected verdict
            # and the run failed, need to fail
            print_ret(ret)
            raise ValueError("Test failed without expected verdict")

        verdict = parse_verdict(ret)

    except subprocess.TimeoutExpired as e:
        # May not be an error
        print("  CPALockator exceeds the timelimit {}".format(timeout))

        verdict = 'TIMEOUT'
        ret = None

    fail_if_incorrect_verdict(ret, expected, verdict)


def prepare_options(config_name, options):
    result = ["-config", "config/includes/lockator/" + config_name]
    return result + add_setprop(options)


def add_setprop(options):
    result = []
    if options:
        for option in options:
            result += ["-setprop", option]
    return result


def run_cpachecker_on_set(options, verdicts, benchmarks, prefix, parallel, timeout):
    proc_num = 2 if parallel else 1
    pool = Pool(processes=proc_num)
    async_res = []

    if prefix:
        benchmarks = prefix + benchmarks

    test_set = glob.glob(benchmarks)
    if len(test_set) == 0:
        # Likely a bug, fail to avoid missing tests
        print("There is no tests for {}".format(benchmarks))
        print("Likely, you are mistaken, in the other case just remove the empty task set")
        raise ValueError("Empty test set {}".format(benchmarks))

    tic = time.perf_counter()
    for test in test_set:
        if verdicts:
            if prefix:
                expected = verdicts[strip_start(test, prefix)]
            else:
                expected = verdicts[test]
        else:
            expected = None

        async_res.append([pool.apply_async(call_cpachecker, args=[options, test, expected, timeout]), test])

    pool.close()
    pool.join()

    total_num = len(async_res)
    failed_tests = []

    for res in async_res:
        ret = res[0]
        test = res[1]
        try:
            ret.get()
        except ValueError:
            # test failure in subprocess
            failed_tests.append(test)

    toc = time.perf_counter()
    fail_num = len(failed_tests)
    print("Run {} benchmarks: {} pass, {} fail".format(total_num, total_num - fail_num, fail_num))
    print("Elapsed time: {:0.2f}s".format(toc - tic))
    if fail_num > 0:
        print("Failed tests:")
        print("\n".join(failed_tests))

        raise ValueError("{} failed tests".format(fail_num))


def strip_start(text, prefix):
    if prefix and text.startswith(prefix):
        return text[len(prefix):]
    return text


def run_job(job_file, is_wip_run):
    with open(job_file, 'r') as file:
        job_config = yaml.safe_load(file)

        for desc, task_config in job_config.items():
            # Default is not WIP
            is_wip_task = task_config.get('wip', True)
            if not is_wip_task and is_wip_run:
                # Skip the task
                continue

            print("Start job " + desc)
            cpa_config = task_config['config']
            to_setprop = task_config.get('setprop', None)

            if "properties" in cpa_config:
                options = prepare_options(cpa_config, to_setprop)
            else:
                options = [cpa_config] + add_setprop(to_setprop)

            if 'spec' in task_config:
                options = options + ["-spec", task_config['spec']]

            if 'options' in task_config:
                options = options + task_config['options']

            is_parallel = task_config.get('parallel', False)
            prefix = task_config.get('prefix', None)

            if 'verdicts' in task_config:
                with open(task_config['verdicts'], 'r') as vfile:
                    test_results = yaml.safe_load(vfile)
                    verdicts = test_results[cpa_config]
            else:
                print("Ideal verdicts are not found")
                verdicts = None

            timeout = task_config.get('timeout', 900)

            benchmarks = task_config['benchmarks']
            if type(benchmarks) is list:
                for bench in benchmarks:
                    run_cpachecker_on_set(options, verdicts, bench, prefix, is_parallel, timeout)
            else:
                run_cpachecker_on_set(options, verdicts, benchmarks, prefix, is_parallel, timeout)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Run integration tests.')
    parser.add_argument('tests', metavar='job_file', type=str, nargs='+',
                        help='job descriptions')
    parser.add_argument('--is_wip', action='store_true', default=False, help='wip run, which is ')

    args = parser.parse_args()
    if args.tests:
        for test_name in args.tests:
            run_job(test_name, args.is_wip)
