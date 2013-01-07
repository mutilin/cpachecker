import subprocess

import benchmark.util as Util

class Tool:
    @staticmethod
    def getExecutable():
        return Util.findExecutable('cpa.sh', 'scripts/cpa.sh')

    @staticmethod
    def getVersion(executable):
        version = ''
        try:
            versionHelpStr = subprocess.Popen([executable, '-help'],
                stdout=subprocess.PIPE).communicate()[0]
            versionHelpStr = Util.decodeToString(versionHelpStr)
            version = ' '.join(versionHelpStr.splitlines()[0].split()[1:])  # first word is 'CPAchecker'
        except IndexError:
            logging.critical('IndexError! Have you built CPAchecker?\n') # TODO better message
            sys.exit()
        return Util.decodeToString(version)

    @staticmethod
    def getName():
        return 'CPAchecker'

    @staticmethod
    def getCmdline(executable, options, sourcefile):
        if ("-stats" not in options):
            options = options + ["-stats"]
        return [executable] + options + [sourcefile]

    @staticmethod
    def getStatus(returncode, returnsignal, output, isTimeout):
        """
        @param returncode: code returned by CPAchecker
        @param returnsignal: signal, which terminated CPAchecker
        @param output: the output of CPAchecker
        @return: status of CPAchecker after executing a run
        """

        def isOutOfNativeMemory(line):
            return ('std::bad_alloc'             in line # C++ out of memory exception (MathSAT)
                 or 'Cannot allocate memory'     in line
                 or line.startswith('out of memory')     # CuDD
                 )

        if returnsignal == 0:
            status = None

        elif returnsignal == 6:
            status = "ABORTED (probably by Mathsat)"

        elif returnsignal == 9:
            if isTimeout:
                status = 'TIMEOUT'
            else:
                status = "KILLED BY SIGNAL 9"

        elif returnsignal == (128+15):
            status = "KILLED"

        else:
            status = "ERROR ({0})".format(returnsignal)

        for line in output.splitlines():
            if 'java.lang.OutOfMemoryError' in line:
                status = 'OUT OF JAVA MEMORY'
            elif isOutOfNativeMemory(line):
                status = 'OUT OF NATIVE MEMORY'
            elif 'There is insufficient memory for the Java Runtime Environment to continue.' in line \
                    or 'cannot allocate memory for thread-local data: ABORT' in line:
                status = 'OUT OF MEMORY'
            elif 'SIGSEGV' in line:
                status = 'SEGMENTATION FAULT'
            elif ((returncode == 0 or returncode == 1)
                    and ('Exception' in line or 'java.lang.AssertionError' in line)
                    and not line.startswith('cbmc')): # ignore "cbmc error output: ... Minisat::OutOfMemoryException"
                status = 'ASSERTION' if 'java.lang.AssertionError' in line else 'EXCEPTION'
            elif 'Could not reserve enough space for object heap' in line:
                status = 'JAVA HEAP ERROR'
            elif line.startswith('Error: '):
                status = 'ERROR'

            elif line.startswith('Verification result: '):
                line = line[21:].strip()
                if line.startswith('SAFE'):
                    newStatus = 'SAFE'
                elif line.startswith('UNSAFE'):
                    newStatus = 'UNSAFE'
                else:
                    newStatus = 'UNKNOWN'
                status = newStatus if status is None else "{0} ({1})".format(status, newStatus)

            elif (status is None) and line.startswith('#Test cases computed:'):
                status = 'OK'
        if status is None:
            status = "UNKNOWN"
        return status

    @staticmethod
    def addColumnValues(output, columns):
        for column in columns:

            # search for the text in output and get its value,
            # stop after the first line, that contains the searched text
            column.value = "-" # default value
            for line in output.splitlines():
                if column.text in line:
                    startPosition = line.find(':') + 1
                    endPosition = line.find('(') # bracket maybe not found -> (-1)
                    if (endPosition == -1):
                        column.value = line[startPosition:].strip()
                    else:
                        column.value = line[startPosition: endPosition].strip()
                    break