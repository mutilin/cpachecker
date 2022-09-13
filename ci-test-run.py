#!/usr/bin/env python3

import subprocess
import glob
import re
# CI script, as it has problems with benchexec due to c groups
cpa_bin = ["./scripts/cpa.sh"]

def call_cpachecker(options, program):
    print("Run CPALockator for {}".format(program))
    cmd_line = cpa_bin + options + [program]
    ret = subprocess.run(cmd_line, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if ret.returncode != 0:
        print(ret.stdout)
        print(ret.stderr)
        raise ValueError("Return code is not zero")
    
    m = re.search('Verification result: (.*)\. ', ret.stdout)
    if re.match('.*-safe\.c', program):
        verdict = m.group(1)
        if verdict != "TRUE":
            print(ret.stdout)
            print(ret.stderr)
            raise ValueError("Expected verdict TRUE and obtain {}".format(verdict))

    if re.match('.*-unsafe\.c', program):
        verdict = m.group(1)
        if verdict != "FALSE":
            print(ret.stdout)
            print(ret.stderr)
            raise ValueError("Expected verdict FALSE and obtain {}".format(verdict))
    

def run_simple_tests():
    print("Run simple tests")
    simple_options = ["-config", "config/includes/lockator/lockStatistics-linux.properties",
        "-setprop", "CompositeCPA.cpas=cpa.location.LocationCPA,cpa.callstack.CallstackCPA,cpa.lock.LockCPA,cpa.predicate.BAMPredicateCPA",
        "-setprop", "cpa.usage.unsafedetector.ignoreEmptyLockset=false",
        "-skipRecursion"]

    for test in glob.glob("test/programs/cpalockator/simple/*.c"):
        call_cpachecker(simple_options, test)

def run_basic_tests():
    print("Run basic tests")
    basic_options = ["-lockator-linux",
        "-setprop", "cpa.usage.unsafedetector.ignoreEmptyLockset=false",
        "-skipRecursion"]
    
    for test in glob.glob("test/programs/cpalockator/threads/*.c"):
        call_cpachecker(basic_options, test)


if __name__ == "__main__":
    run_simple_tests()
    run_basic_tests()