// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import java.io.PrintStream;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class SignalStatistics implements Statistics {

  public final StatTimer transfer = new StatTimer("Time for transfer relation");
  public final StatTimer lessOrEquals = new StatTimer("Time for isLessOrEquals");
  public final StatCounter createdLocalSignals = new StatCounter("Number of created local signals");
  public final StatCounter createdGlobalSignals =
      new StatCounter("Number of created global signals");
  public final StatCounter sendSignals = new StatCounter("Number of sent signals");
  public final StatCounter receiveSignals = new StatCounter("Number of receive signals");
  public final StatInt trackedSignals = new StatInt(StatKind.AVG, "Number of signals in states");

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    StatisticsWriter writer = StatisticsWriter.writingStatisticsTo(pOut);
    writer.put(transfer)
        .put(lessOrEquals)
        .put(createdLocalSignals)
        .put(createdGlobalSignals)
        .put(sendSignals)
        .put(receiveSignals)
        .put(trackedSignals);
  }

  @Override
  public String getName() {
    return "SignalCPA";
  }
}
