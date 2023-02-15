#!/usr/bin/env python3

import subprocess
import glob
import re
import argparse
import os
import yaml
import time
import multiprocessing
from multiprocessing import Pool
# CI script, as it has problems with benchexec due to c groups
cpa_bin = ["./scripts/cpa.sh"]

def fail(ret, expected, real):
    if ret is not None:
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
        if (expected != real):
            fail(ret, expected, real)

def run_cpachecker(options, program, cpa_timeout = 900):
    print("Run CPALockator for {}".format(program))
    cmd_line = cpa_bin + options + [program]
    tic = time.perf_counter()
    try:
        ret = subprocess.run(cmd_line, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=cpa_timeout)
        toc = time.perf_counter()
        print("Elapsed time: {:0.2f}s".format(toc - tic))
        if ret.returncode != 0:
            print(" ".join(cmd_line))
            print(ret.stdout)
            print(ret.stderr)
            raise ValueError("Return code is not zero")
        return ret

    except subprocess.TimeoutExpired as e:
        # May not be an error
        print("CPALockator exceeds the timelimit")
        print(" ".join(cmd_line))
        raise e

def to_string_verdict(verdict):
    if verdict == True:
        return "TRUE"
    if verdict == False:
        return "FALSE"

    raise ValueError("Unknown verdict {}".format(verdict))

def parse_verdict(ret):
    m = re.search('Verification result: (.*)\. ', ret.stdout)
    if m is not None:
        verdict = m.group(1)
        return verdict
        
    m = re.search('Shutdown requested', ret.stderr)
    if m is not None:
        return 'TIMEOUT'

    m = re.search('Analysis interrupted', ret.stderr)
    if m is not None:
        return 'TIMEOUT'
    
    m = re.search('Error: ', ret.stderr)
    if m is not None:
        return 'ERROR'

    m = re.search('Exception in thread', ret.stderr)
    if m is not None:
        return 'EXCEPTION'

    print(" ".join(ret.args))
    print(ret.stdout)
    print(ret.stderr)
    raise ValueError("No verification result")

def call_cpachecker(options, program, expected, timeout_check = True):
    try:
        ret = run_cpachecker(options, program)
        verdict = parse_verdict(ret)
    except subprocess.TimeoutExpired as e:
        if timeout_check:
            verdict = 'TIMEOUT'
            ret = None
        else:
            # Do not check timeouts right now
            # because ldv-klever benchmarks do not support multiple verdicts
            raise e;
    except ValueError as e:
        verdict = 'EXCEPTION'
        ret = None
    
    if expected is None:
        # Used for debug mostly
        return verdict
    else:
        fail_if_incorrect_verdict(ret, expected, verdict)

def prepare_options(config_name, options):
    result = ["-config", "config/includes/lockator/" + config_name + ".properties"]
    return result + add_setprop(options)

def add_setprop(options):
    result = []
    for option in options:
        result += ["-setprop", option]
    return result

def run_cpachecker_on_set(options, verdicts, benchmarks, prefix = None):
    pool = Pool(processes=2)
    for test in glob.glob(benchmarks):
        if prefix:            
            expected = verdicts[strip_start(test, prefix)]
        else:
            expected = verdicts[test]

        pool.apply_async(call_cpachecker, args=[options, test, expected])

    pool.close()
    pool.join()

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

def run_simple_tests():
    # Checks the basic configs on a small simple tests

    def run_simple_with_options(options, verdicts):
        run_cpachecker_on_set(options, verdicts, "test/programs/cpalockator/simple/*.c")

    def run_common_checks(type):
        with open('ci-config/' + type + '-test-verdicts.yml', 'r') as file:
            test_results = yaml.safe_load(file)
            run_cpachecker_on_set(["-lockator-linux"], test_results['lockator-linux.properties'], "test/programs/cpalockator/" + type + "/*.c")            
            run_cpachecker_on_set(["-lockator-threadmodular-linux"], test_results['lockator-threadmodular-linux.properties'], "test/programs/cpalockator/" + type + "/*.c")            
            run_cpachecker_on_set(["-lockator-linux-lightweight"], test_results['lockator-linux-lightweight.properties'], "test/programs/cpalockator/" + type + "/*.c")

    # Note, there is no threads
    print("Run simple tests")
    with open('ci-config/simple-test-verdicts.yml', 'r') as file:
        test_results = yaml.safe_load(file)

        simple_options = prepare_options("lockStatistics-linux", 
            ["CompositeCPA.cpas=cpa.location.LocationCPA,cpa.callstack.CallstackCPA,cpa.lock.LockCPA,cpa.predicate.BAMPredicateCPA",
            "cpa.usage.unsafedetector.ignoreEmptyLockset=false"])
        
        run_simple_with_options(simple_options, test_results['lockStatistics-linux.properties'])

        # The verdicts are set for the one configuration, then just check, that the tool does not fail
        simple_options = prepare_options("lockStatistics-linux-lightweight",
            ["CompositeCPA.cpas=cpa.location.LocationCPA,cpa.callstack.CallstackCPA,cpa.lock.LockCPA",
            "cpa.usage.unsafedetector.ignoreEmptyLockset=false"])
        run_simple_with_options(simple_options, test_results['lockStatistics-linux-lightweight.properties'])

        simple_options = prepare_options("lockStatistics-threadmodular-linux",
            ["CompositeCPA.cpas=cpa.location.LocationCPA,cpa.callstack.CallstackCPA,cpa.lock.LockCPA,cpa.predicate.PredicateCPA",
            "cpa.usage.unsafedetector.ignoreEmptyLockset=false"])
        run_simple_with_options(simple_options, test_results['lockStatistics-threadmodular-linux.properties'])

        simple_options = prepare_options("lockStatistics-linux",
            ["CompositeCPA.cpas=cpa.location.LocationCPA,cpa.callstack.CallstackCPA,cpa.thread.ThreadCPA,cpa.predicate.BAMPredicateCPA"])
        run_simple_with_options(simple_options, test_results['lockStatistics-linux-bam.properties'])

    run_common_checks('simple')
    run_common_checks('threads')
    run_common_checks('pointers')

def run_uppsala_tests():
    def run_uppsala_tests(options, dirs, key_config, verdict_file):
        with open(verdict_file, 'r') as file:
            test_results = yaml.safe_load(file)
            for dir in dirs:
                run_cpachecker_on_set(options, test_results[key_config], dir + "*.i", os.environ['UPPSALA_BENCHMARKS'])

    # Note, there is no threads
    print("Run uppsala tests")

    options = ["-predicateAnalysis-threadmodular",
        "-setprop", "cpa.predicate.refinement.performThreadEffectRefinement=true",
        "-setprop", "cpa.thread.skipTheSameThread=true"]
    
    benchmark_dirs = [
        os.environ['UPPSALA_BENCHMARKS'] + "/sv-races/",
        os.environ['UPPSALA_BENCHMARKS'] + "/adhoc/",
        os.environ['UPPSALA_BENCHMARKS']
    ]

    run_uppsala_tests(options, benchmark_dirs, 'predicateAnalysis-threadmodular.properties', 'ci-config/uppsala-verdicts.yml')

def run_product_lines():

    def run_simple_with_options(config, options):
        run_options = prepare_options(config, options)
        for test in glob.glob("test/programs/cpalockator/threads/*.c"):
            run_cpachecker(run_options, test)

    def check_options(config, options_list):
        for option in options_list:
            run_simple_with_options(config, [option])

    options_list = [
        "cpa.usage.useSoundRegions=true",
        "cpa.usage.bindArgsFunctions=true",
        "cpa.usage.filterAliases=false",
        "cpa.usage.useConcurrentExtraction=false",
        "cpa.thread.useAggressiveReduction=true",
        "cpa.thread.mode=ENVIRONMENT",
        "cpa.thread.mode=SIMPLE",
        "cpa.thread.supportSelfCreation=true",
        "cpa.thread.skipTheSameThread=true"
        ]

    check_options("lockStatistics-linux", options_list)

    # Specific case for deadlocks:
    run_simple_with_options("lockStatistics-linux",
        ["cpa.lock.analysisMode=DEADLOCK",
        "cpa.usage.unsafedetector.unsafeMode=DEADLOCKCIRCULAR"])

    run_simple_with_options("lockStatistics-linux",
        ["cpa.lock.analysisMode=DEADLOCK",
        "cpa.usage.unsafedetector.intLock=dummy",
        "cpa.usage.unsafedetector.unsafeMode=DEADLOCKDISPATCH"])

    options_list = [
        "cpa.usage.useSoundRegions=true",
        "cpa.thread.mode=ENVIRONMENT",
        "cpa.thread.mode=SIMPLE",
        "cpa.thread.supportSelfCreation=true",
        "cpa.thread.skipTheSameThread=true",
        "cpa.predicate.joinEffectsIntoUndefs=false",
        "cpa.predicate.useUndefFunctions=false",
        "cpa.predicate.applyRelevantEffects=true",
        "cpa.usage.refinement.refinementChain=IdentifierIterator,PointIterator,UsageIterator,SinglePathIterator,ImprecisePredicateRefiner",
        "cpa.predicate.useHavocAbstraction=true",
        "cpa.predicate.abstractionLattice=true"
        ]

    check_options("lockStatistics-threadmodular-linux", options_list)

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
                    try:
                        if 'expected_verdict' in properties:
                            verdict = test_desc['properties'][0]['expected_verdict']
                            call_cpachecker(options, klever_dir + test, to_string_verdict(verdict), False)
                        else:
                            # there are unknown verdicts
                            run_cpachecker(options, klever_dir + test)
                    except subprocess.TimeoutExpired:
                        # Timeouts are possible
                        pass

    print("Run ldv klever tests")
    base_options = add_setprop(["cpa.callstack.unsupportedFunctions=__VERIFIER_nonexisting_dummy_function",
        "cpa.predicate.allowedUnsupportedFunctions=memset,memcpy,__builtin_add_overflow,__builtin_mul_overflow,__builtin_va_arg",
        "counterexample.export.extendedWitnessFile=witness.%d.graphml",
        "cpa.arg.witness.removeInsufficientEdges=false",
        "additionalCoverage.file=additionalCoverage.info",
        "shutdown.timeout=100",
        "counterexample.export.graphml=witness.%d.graphml"]) + ["-noout", "-heap", "2400m", "-64"]

    run_klever_list('ci-config/ldv-klever-benchmarks.list', base_options + ["-lockator-threadmodular-linux"])

    run_klever_list('ci-config/ldv-klever-rcu-benchmarks.list', base_options + ["-rcucpa"])

def run_ldv_tests():

    def is_ignored(test, list):
        for ignore in list:
            if ignore in test:
                return True
        return False

    def run_with_options(options):
        benchmarks_dir = os.environ['LDV_BENCHMARKS'] + "/linux-4.2.6-races"
        verdicts = ["/Unsafes/"] # "Safes", "Unknowns"
        with open('ci-config/ldv-benchmarks-ignore.list') as f:
            ignored_tests = f.read().splitlines()
            for verdict in verdicts:
                for test in glob.glob(benchmarks_dir + verdict + "*.cil.i"):
                    if is_ignored(test, ignored_tests):
                        continue
                    run_cpachecker(options, test)
    
    print("Run ldv benchmarks")
    base_options = ["-lockator-threadmodular-linux"] + add_setprop([
        "cpa.predicate.useUndefFunctions=false",
        "cpa.predicate.joinEffectsIntoUndefs=false",
        "cpa.predicate.refinement.predicateBasisStrategy=subgraph",
        "cpa.predicate.applyRelevantEffects=true",
        "cpa.lock.refinement=false",
        "cpa.usage.refinement.refinementChain=IdentifierIterator,PointIterator,UsageIterator,SinglePathIterator,PredicateRefiner",
        "limits.time.cpu=900s",
        "shutdown.timeout=1000"])

    run_with_options(base_options)

    base_options = ["-lockator-linux-without-shared"] + add_setprop([
        "limits.time.cpu=900s",
        "shutdown.timeout=1000"])

    run_with_options(base_options)

def run_cfa_mutation():
    print("Run cfa mutation")

    base_options = ["-lockator-threadmodular-linux"] + add_setprop([
        "cfaMutation=true",
        "limits.time.wall=1500s",
        "limits.time.cpu=-1ns",
        "parser.readLineDirectives=false",
        "cfa.threads.addAssumptions=false",
        "cpa.usage.outputType=Klever3",
        "output.disable=false",
        "cfa.export=true",
        "cfa.exportPerFunction=true",
        "cfa.exportToC=true",
        "cfa.serialize=true"])
    
    strategies = ["cfaMutation.strategies=FunctionBodyRemover,AssumeEdgesRemover,StatementChainRemover,StatementEdgeRemover,ExpressionRemover,DeclarationEdgeRemover,FunctionBodyRemover,GlobalDeclarationRemover",
        "cfaMutation.strategies=ExpressionRemover,DeclarationEdgeRemover,FunctionBodyRemover,GlobalDeclarationRemover"
        # Verify.error at CFA construction
        # "cfaMutation.strategies=AssumeEdgesRemover,StatementChainRemover,StatementEdgeRemover,ExpressionRemover,DeclarationEdgeRemover,GlobalDeclarationRemover",
        # Takes lot of time
        # "cfaMutation.strategies=FunctionBodyRemover",
        # "cfaMutation.strategies=StatementChainRemover"
        ]

    test = "test/programs/issues-for-mutation/cpalockator.i"

    for strategy in strategies:
        options = base_options + add_setprop([strategy])
        run_cpachecker(options, test, cpa_timeout=1600)

def run_sv_benchmarks():
    def test_config(config, directory):
        with open('ci-config/sv-benchmarks-verdicts.yml', 'r') as file:
            test_results = yaml.safe_load(file)
            
            options = [config] + add_setprop(["limits.time.cpu=900s",
                "shutdown.timeout=1000",
                "cpa.thread.supportSelfCreation=true"]) + ["-noout"]
            benchmarks = os.environ['SV_BENCHMARKS'] + directory + "*.i"
            run_cpachecker_on_set(options, test_results[config], benchmarks , os.environ['SV_BENCHMARKS'])

    print("Run sv benchmarks")

    test_config('-predicateAnalysis-threadmodular', "ldv-linux-3.14-races/")
    test_config('-predicateAnalysis-threadmodular', "ldv-races/")
    # Unsupported parameter expression tids[i]
    # test_config("-predicateAnalysis-threadmodular", "/pthread/")
    test_config('-predicateAnalysis-threadmodular', "pthread-atomic/")
    test_config('-predicateAnalysis-threadmodular', "pthread-ext/")
    test_config('-predicateAnalysis-threadmodular', "pthread-lit/")
    test_config('-predicateAnalysis-threadmodular', "pthread-driver-races/")
    # Unsupported parameter expression t[i]
    # test_config("-predicateAnalysis-threadmodular", "/pthread-nondet/")

    # fast check of different options on ldv-races
    test_config('-predicateAnalysis-threadmodular-refinement', "ldv-races/")
    test_config('-predicateAnalysis-threadmodular-reordered', "ldv-races/")
    test_config('-predicateAnalysis-tm-ta', "ldv-races/")
    test_config('-predicateValueAnalysis-threadmodular', "ldv-races/")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Run integration tests.')
    parser.add_argument('tests', metavar='test_name', type=ascii, nargs='+',
                        help='test identifiers')

    args = parser.parse_args()
    for test_name in args.tests:
        if test_name == "'simple'":
            run_simple_tests()
        if test_name == "'uppsala'":
            run_uppsala_tests()
        if test_name == "'ldv-klever'":
            run_ldv_klever_tests()
        if test_name == "'ldv-benchmarks'":
            run_ldv_tests()
        if test_name == "'product-lines'":
            run_product_lines()
        if test_name == "'cfa-mutation'":
            run_cfa_mutation()
        if test_name == "'sv-benchmarks'":
            run_sv_benchmarks()
