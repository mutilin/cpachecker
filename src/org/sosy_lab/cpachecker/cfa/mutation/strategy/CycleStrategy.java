/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cfa.mutation.strategy;

import com.google.common.collect.ImmutableList;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class CycleStrategy extends AbstractCFAMutationStrategy {
  private CycleStatistics thisCycle = new CycleStatistics(1);
  private final AbstractCFAMutationStrategy strategy;
  private CycleStrategyStatistics stats;

  private static class CycleStatistics extends AbstractMutationStatistics {
    private final long cycle;
    private final Collection<Statistics> statsOfUsedStrategy = new ArrayList<>();

    public CycleStatistics(long pCycle) {
      cycle = pCycle;
    }
  }

  private static class CycleStrategyStatistics extends AbstractMutationStatistics {
    private final StatCounter cycles = new StatCounter("cycles");
    private final List<CycleStatistics> cycleStats = new ArrayList<>();

    @Override
    public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
      StatisticsWriter w = StatisticsWriter.writingStatisticsTo(pOut);
      w.beginLevel().put(getName(), "").put(cycles).put(rounds).put(rollbacks);
      if (cycles.getUpdateCount() > 0) {
        w.beginLevel();
        for (CycleStatistics c : cycleStats) {
          w.put("Cycle " + c.cycle, "").put(c.rounds).put(c.rollbacks).beginLevel();
          for (Statistics s : c.statsOfUsedStrategy) {
            s.printStatistics(pOut, pResult, pReached);
          }
          w.endLevel();
        }
        w.endLevel();
      }
      w.endLevel();
    }

    @Override
    public String getName() {
      return "CycleStrategy";
    }
  }

  public CycleStrategy(LogManager pLogger) {
    super(pLogger);
    strategy =
        new CompositeStrategy(
            pLogger,
            ImmutableList.of(
                //   1. Remove unneeded assumes and statements.
                // First, remove statements if possible
                new StatementNodeStrategy(pLogger, 2),
                new DummyStrategy(pLogger),
                // Second, remove AssumeEdges if possible
                new SimpleAssumeEdgeStrategy(pLogger, 2),
                new DummyStrategy(pLogger),
                // Then remove blank edges
                new BlankNodeStrategy(pLogger, 1),
                new DummyStrategy(pLogger),

                //   2. Remove loops on nodes (edges from node to itself).
                new LoopOnNodeStrategy(pLogger, 2),
                new DummyStrategy(pLogger),
                //   Now we can remove delooped blank edges.
                new BlankNodeStrategy(pLogger, 2),
                new DummyStrategy(pLogger),
                //   3. Remove remained branches when both branches are
                //      chains with end on same node, or either branch
                //      is a chain ending on exit or termination node.
                new BranchStrategy(pLogger, 2),
                new DummyStrategy(pLogger),

                // some thread creating statements could be gone now
                // so some functions may become deletable
                // new FunctionStrategy(pConfig, pLogger, 100, false, ImmutableSet.of("main")),

                //   4. Remove unneeded declarations.
                new DeclarationStrategy(pLogger, 1),
                new DummyStrategy(pLogger)));
    stats = new CycleStrategyStatistics();
  }

  @Override
  public boolean mutate(ParseResult pParseResult) {
    if (strategy.mutate(pParseResult)) {
      stats.rounds.inc();
      if (thisCycle.rounds.getValue() == 0) {
        stats.cycles.inc();
      }
      thisCycle.rounds.inc();
      return true;
    }
    return nextCycle(pParseResult);
  }

  private boolean nextCycle(ParseResult pParseResult) {
    if (thisCycle.rounds.getValue() == 0) {
      return false;
    } else {
      strategy.makeAftermath(pParseResult);
      strategy.collectStatistics(thisCycle.statsOfUsedStrategy);
      stats.cycleStats.add(thisCycle);
      thisCycle = new CycleStatistics(stats.cycles.getUpdateCount());
      return mutate(pParseResult);
    }
  }

  @Override
  public void rollback(ParseResult pParseResult) {
    stats.rollbacks.inc();
    thisCycle.rollbacks.inc();
    strategy.rollback(pParseResult);
  }

  @Override
  public String toString() {
    return super.toString() + ", " + stats.cycles.getValue() + " cycles";
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (thisCycle.rounds.getUpdateCount() > 0) {
      strategy.collectStatistics(thisCycle.statsOfUsedStrategy);
      stats.cycleStats.add(thisCycle);
    }
    pStatsCollection.add(stats);

    stats = new CycleStrategyStatistics();
    thisCycle = new CycleStatistics(1);
  }

  @Override
  public void makeAftermath(ParseResult pParseResult) {
    strategy.makeAftermath(pParseResult);
  }
}
