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

def fail(ret, expected, real):
    if ret:
        print(" ".join(ret.args))
        print(ret.stdout)
        print(ret.stderr)
    raise ValueError("Expected verdict {} and obtain {}".format(expected, real))

def fail_if_incorrect_verdict(ret, expected, real):
    if expected is None:
        # No expected verdict
        return
    if type(expected) is list:
        # Several possible verdicts, like flacky false-timeout
        for exp in expected:
            if exp == real:
                return
        fail(ret, expected, real)
    else:
        if expected != real:
            fail(ret, expected, real)

def parse_verdict(ret):
    m = re.search('Verification result: (.*)\. ', ret.stdout)
    if m:
        verdict = m.group(1)
        return verdict
        
    m = re.search('Shutdown requested', ret.stderr)
    if m:
        return 'TIMEOUT'

    m = re.search('Analysis interrupted', ret.stderr)
    if m:
        return 'TIMEOUT'
    
    m = re.search('Error: ', ret.stderr)
    if m:
        return 'ERROR'

    m = re.search('Exception in thread', ret.stderr)
    if m:
        return 'EXCEPTION'

    print(" ".join(ret.args))
    print(ret.stdout)
    print(ret.stderr)
    raise ValueError("No verification result")

def call_cpachecker(options, program, expected, timeout=900):
    try:
        print("Run CPALockator for {}".format(program))
        cmd_line = cpa_bin + options + [program]

        tic = time.perf_counter()
        ret = subprocess.run(cmd_line, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=timeout)
        toc = time.perf_counter()
        print("Elapsed time: {:0.2f}s".format(toc - tic))

        if ret.returncode != 0:
            print(" ".join(cmd_line))
            print(ret.stdout)
            print(ret.stderr)

        verdict = parse_verdict(ret)

    except subprocess.TimeoutExpired as e:
        # May not be an error
        print("CPALockator exceeds the timelimit {}".format(timeout))
        print(" ".join(cmd_line))

        verdict = 'TIMEOUT'
        ret = None
    
    if expected:
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

def run_cpachecker_on_set(options, verdicts, benchmarks, prefix = None, parallel = False, timeout=900):
    if parallel:
        procNum = 2
    else:
        procNum = 1
    pool = Pool(processes=procNum)
    async_res = []

    if prefix:
        benchmarks = prefix + benchmarks
    
    for test in glob.glob(benchmarks):
        if verdicts:
            if prefix:            
                expected = verdicts[strip_start(test, prefix)]
            else:
                expected = verdicts[test]
        else:
            expected = None

        async_res.append(pool.apply_async(call_cpachecker, args=[options, test, expected, timeout]))

    pool.close()
    pool.join()

    for res in async_res:
        try:
            res.get()
        except ValueError as e:
            # test failure in subprocess
            raise e

# Mostly for debug
def obtain_verdicts(options, benchmarks):
    result = {}
    for test in benchmarks:
        verdict = call_cpachecker(options, test, None)
        result.update({strip_start(test, os.environ['UPPSALA_BENCHMARKS']): verdict})
    return result

def yaml_dump(data):
    with open('data.yaml', 'w') as fp:
        yaml.dump(data, fp)

def strip_start(text, prefix):
    if prefix and text.startswith(prefix):
        return text[len(prefix):]
    return text

def getOrDefault(dict, key, default = None):
    if key in dict:
        return dict[key]
    else:
        return default

def run_job(job_file, is_wip_run):
    with open(job_file, 'r') as file:
        job_config = yaml.safe_load(file)

        for desc, task_config in job_config.items():

            # Default is not WIP
            is_wip_task = getOrDefault(task_config, 'wip', True)
            if is_wip_task is False and is_wip_run is True:
                # Skip the task
                continue
            
            print("start job " + desc)
            cpa_config = task_config['config']
            to_setprop = getOrDefault(task_config, 'setprop')

            if "properties" in cpa_config:
                options = prepare_options(cpa_config, to_setprop)
            else:
                options = [cpa_config] + add_setprop(to_setprop)

            if 'spec' in task_config:
                options = options + ["-spec", task_config['spec']]

            is_parallel = getOrDefault(task_config, 'parallel')
            prefix = getOrDefault(task_config, 'prefix')

            if 'verdicts' in task_config:
                with open(task_config['verdicts'], 'r') as vfile:
                    test_results = yaml.safe_load(vfile)
                    verdicts = test_results[cpa_config]
            else:
                verdicts = None
            
            timeout = getOrDefault(task_config, 'timeout', 900)

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
