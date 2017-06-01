import argparse
import json
import os
import subprocess
import sys
import time

def load_plan(path):
    print("Loading plan from {}".format(path))

    with open(path) as f:
        return json.load(f)

def write_object_file_plan(object_file_plan, object_file_plan_path):
    with open(object_file_plan_path, "w") as f:
        f.write("FILE {}\n".format(object_file_plan["object file"]))

        for function in object_file_plan["functions"]:
            f.write("FUNCTION {}\n".format(function["name"]))

            for called_function in function["called functions"]:
                f.write("CALLS {} {}\n".format(called_function["name"], called_function["object file"]))

def run(cpachecker, sources, annotations, plan, debug, overview_log):
    print("Running plan")
    total_start = time.time()

    successes = 0
    failures = 0
    errors = 0

    overview_file = open(overview_log, "w") if overview_log is not None else sys.stdout

    with overview_file:
        for i, object_file_plan in enumerate(plan):
            name = object_file_plan["object file"]
            path = os.path.join(sources, name, os.path.basename(name))
            log_dir = os.path.join(os.path.abspath(annotations), name)
            overview_file.write("Analysing object file #{}/{}: {} ({} functions)".format(i + 1, len(plan), name, len(object_file_plan["functions"])))
            overview_file.flush()

            object_file_plan_path = "object_file_plan.txt"
            write_object_file_plan(object_file_plan, object_file_plan_path)

            args = [
                "scripts/cpa.sh",
                "-config", "config/ldv-deref.properties",
                "-spec", "config/specification/default.spc",
                os.path.abspath(path),
                "-setprop", "nullDerefArgAnnotationAlgorithm.annotationDirectory={}".format(os.path.abspath(annotations)),
                "-setprop", "analysis.entryFunction={}".format(object_file_plan["functions"][0]["name"]),
                "-setprop", "nullDerefArgAnnotationAlgorithm.plan={}".format(os.path.abspath(object_file_plan_path)),
                "-setprop", "parser.usePreprocessor=true"
            ]

            if debug:
                args.extend([
                    "-setprop", "nullDerefArgAnnotationAlgorithm.distinctTempSpecNames=true",
                    "-setprop", "log.consoleLevel=ALL",
                    "-setprop", "log.consoleExclude=CONFIG"
                ])

            os.makedirs(log_dir)
            log_path = os.path.join(log_dir, "log.txt")

            with open(log_path, "w") as f:
                f.write("RUN {}\n\n".format(" ".join(args)))
                f.flush()

                start = time.time()
                popen = subprocess.Popen(args, cwd=cpachecker, stdout=f, stderr=subprocess.STDOUT, universal_newlines=True)
                popen.wait()
                finish = time.time()

            with open(log_path) as f:
                output = f.read()

            if popen.returncode != 0:
                status = "error"
                errors += 1
            elif "Verification result: UNKNOWN, incomplete analysis." in output:
                status = "success"
                successes += 1
            else:
                status = "failure"
                failures += 1

            overview_file.write(" - {}, took {:.2f} seconds\n".format(status, finish - start))
            overview_file.flush()

        total_finish = time.time()
        overview_file.write("Completed - {} successes, {} failures, {} errors, took {:.2f} seconds\n".format(successes, failures, errors, total_finish - total_start))
        overview_file.flush()

def main():
    parser = argparse.ArgumentParser(
        description="Null deref annotation algorithm runner.")

    parser.add_argument(
        "cpachecker",
        help="Path to cpachecker directory.")

    parser.add_argument(
        "sources",
        help="Path to preprocessed sources directory.")

    parser.add_argument(
        "plan",
        help="Path to a JSON file containing plan.")

    parser.add_argument(
        "annotations",
        help="Path to annotation directory, it will be created if missing.")

    parser.add_argument(
        "--debug",
        help="Use distinct names for temporary spec files and make more logs.",
        action="store_true",
        default=False
    )

    parser.add_argument(
        "--log",
        help="Write overview information into a file instead of stdout."
    )

    args = parser.parse_args()
    plan = load_plan(args.plan)
    run(args.cpachecker, args.sources, args.annotations, plan, args.debug, args.log)

if __name__ == "__main__":
    main()
