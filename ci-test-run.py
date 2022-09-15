#!/usr/bin/env python3

import subprocess
import glob
import re
import argparse
import os
import yaml
# CI script, as it has problems with benchexec due to c groups
cpa_bin = ["./scripts/cpa.sh"]

def fail_if_incorrect_verdict(ret, expected, real):
    if (expected != real):
        print(ret.stdout)
        print(ret.stderr)
        raise ValueError("Expected verdict {} and obtain {}".format(expected, real))

def run_cpachecker(options, program):
    print("Run CPALockator for {}".format(program))
    cmd_line = cpa_bin + options + [program]
    ret = subprocess.run(cmd_line, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if ret.returncode != 0:
        print(cmd_line)
        print(ret.stdout)
        print(ret.stderr)
        raise ValueError("Return code is not zero")
    return ret

def get_verdict_for_simple_test(program):
    # For simple tests
    if re.match('.*-safe\.c', program):
        return "TRUE"

    if re.match('.*-unsafe\.c', program):
        return "FALSE"
    
    raise ValueError("Cannot determine expected verdict for {}".format(program))

def to_string_verdict(verdict):
    if verdict == True:
        return "TRUE"
    if verdict == False:
        return "FALSE"

    raise ValueError("Unknown verdict {}".format(verdict))

def call_cpachecker(options, program, expected):
    ret = run_cpachecker(options, program)

    m = re.search('Verification result: (.*)\. ', ret.stdout)
    if m is None:
        print(ret.stdout)
        print(ret.stderr)
        raise ValueError("No verification result")
    
    verdict = m.group(1)
    fail_if_incorrect_verdict(ret, expected, verdict)

def run_simple_tests():
    print("Run simple tests")
    simple_options = ["-config", "config/includes/lockator/lockStatistics-linux.properties",
        "-setprop", "CompositeCPA.cpas=cpa.location.LocationCPA,cpa.callstack.CallstackCPA,cpa.lock.LockCPA,cpa.predicate.BAMPredicateCPA",
        "-setprop", "cpa.usage.unsafedetector.ignoreEmptyLockset=false",
        "-skipRecursion"]

    for test in glob.glob("test/programs/cpalockator/simple/*.c"):
        verdict = get_verdict_for_simple_test(test)
        call_cpachecker(simple_options, test, verdict)

def run_basic_tests():
    print("Run basic tests")
    basic_options = ["-lockator-linux",
        "-setprop", "cpa.usage.unsafedetector.ignoreEmptyLockset=false",
        "-skipRecursion"]
    
    for test in glob.glob("test/programs/cpalockator/threads/*.c"):
        verdict = get_verdict_for_simple_test(test)
        call_cpachecker(basic_options, test, verdict)

def run_ldv_klever_tests():

    def run_klever_list(list, options):        
        with open(list) as f:
            klever_tests = f.read().splitlines()
            klever_dir = os.environ['KLEVER_BENCHMARKS']
            for test in klever_tests:
                if test.startswith('#'):
                    # comment
                    continue
                m = re.search('(.*).cil.i', test)
                base_name = m.group(1)
                yml_info = klever_dir + base_name + ".yml"
                with open(yml_info, 'r') as file:
                    test_desc = yaml.safe_load(file)
                    properties = test_desc['properties'][0]
                    if 'expected_verdict' in properties:
                        verdict = test_desc['properties'][0]['expected_verdict']
                        call_cpachecker(options, klever_dir + test, to_string_verdict(verdict))
                    else:
                        # there are unknown verdicts
                        run_cpachecker(options, klever_dir + test)

    print("Run ldv klever tests")
    base_options = ["-setprop", "cpa.callstack.unsupportedFunctions=__VERIFIER_nonexisting_dummy_function",
        "-setprop", "cpa.predicate.allowedUnsupportedFunctions=memset,memcpy,__builtin_add_overflow,__builtin_mul_overflow,__builtin_va_arg",
        "-setprop", "cpa.value.allowedUnsupportedFunctions=memset,memcpy,__builtin_add_overflow,__builtin_mul_overflow,__builtin_va_arg",
        "-setprop", "counterexample.export.extendedWitnessFile=witness.%d.graphml",
        "-setprop", "counterexample.export.exportExtendedWitness=true",
        "-setprop", "counterexample.export.compressWitness=false",
        "-setprop", "cpa.arg.witness.removeInsufficientEdges=false",
        "-setprop", "counterexample.export.exportCounterexampleCoverage=true",
        "-setprop", "counterexample.export.prefixAdditionalCoverageFile=Counterexample.%d.additionalCoverage.info",
        "-setprop", "additionalCoverage.file=additionalCoverage.info",
        "-setprop", "shutdown.timeout=100",
        "-setprop", "counterexample.export.graphml=witness.%d.graphml",
        "-noout", "-heap", "2400m", "-64"]

    # run_klever_list('ci-config/ldv-klever-benchmarks.list', base_options + ["-lockator-threadmodular-linux"])

    run_klever_list('ci-config/ldv-klever-rcu-benchmarks.list', base_options + ["-rcucpa"])

def run_ldv_tests():

    def is_ignored(test, list):
        for ignore in list:
            if ignore in test:
                return True
        return False
    
    print("Run ldv benchmarks")
    base_options = ["-lockator-threadmodular-linux",
        "-setprop", "cpa.predicate.useUndefFunctions=false",
        "-setprop", "cpa.predicate.joinEffectsIntoUndefs=false",
        "-setprop", "cpa.predicate.refinement.predicateBasisStrategy=subgraph",
        "-setprop", "cpa.predicate.applyRelevantEffects=true",
        "-setprop", "cpa.lock.refinement=false",
        "-setprop", "cpa.usage.refinement.refinementChain=IdentifierIterator,PointIterator,UsageIterator,SinglePathIterator,PredicateRefiner",
        "-setprop", "limits.time.cpu=900s",
        "-setprop", "shutdown.timeout=1000"]

    benchmarks_dir = os.environ['LDV_BENCHMARKS'] + "/linux-4.2.6-races"
    verdicts = ["/Unsafes/"] # "Safes", "Unknowns"
    with open('ci-config/ldv-benchmarks-ignore.list') as f:
        ignored_tests = f.read().splitlines()
        for verdict in verdicts:
            for test in glob.glob(benchmarks_dir + verdict + "*.cil.i"):
                if is_ignored(test, ignored_tests):
                    continue
                run_cpachecker(base_options, test)
    

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Run integration tests.')
    parser.add_argument('tests', metavar='test_name', type=ascii, nargs='+',
                        help='test identifiers')

    args = parser.parse_args()
    for test_name in args.tests:
        if test_name == "'simple'":
            run_simple_tests()
        if test_name == "'basic'":
            run_basic_tests()
        if test_name == "'ldv-klever'":
            run_ldv_klever_tests()
        if test_name == "'ldv-benchmarks'":
            run_ldv_tests()