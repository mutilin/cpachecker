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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public abstract class GenericCFAMutationStrategy<ObjectKey, RollbackInfo>
    extends AbstractCFAMutationStrategy {

  private Collection<ObjectKey> objectsBefore;
  private Collection<ObjectKey> objectsAppeared = new HashSet<>();
  private Collection<ObjectKey> objectsAfter;

  private Deque<RollbackInfo> currentMutation = new ArrayDeque<>();
  private final Set<ObjectKey> previousMutations = new HashSet<>();
  private final int rate;
  private int depth;
  private int batchNum = -1;
  private int batchSize = -1;
  private int batchCount = -1;
  private final boolean tryAllAtFirst;
  private final String objectsDescription;

  private OnePassMutationStatistics stats;
  private boolean exportObjectsEveryRound = false;
  private Path exportFile = Paths.get("output/functions.txt");

  protected static class OnePassMutationStatistics extends AbstractMutationStatistics {
    // cfa objects for strategy to deal with
    protected final StatInt objectsBeforePass;
    // cfa objects remained because they can't be mutated out or because strategy ran out of rounds
    protected final StatInt objectsAppearedDuringPass;
    // cfa objects remained because they can't be mutated out or because strategy ran out of rounds
    protected final StatInt objectsRemainedAfterPass;
    private StatInt objectsAppearedAfterPass;
    // cfa objects to mutate in last seen cfa (appeared because of other strategies)
    protected final StatInt objectsForNextPass;
    private final String strategyClassName;

    public OnePassMutationStatistics(String pName, String pObjectsDescription) {
      strategyClassName = pName;
      objectsBeforePass =
          new StatInt(StatKind.SUM, pObjectsDescription + " were in CFA before pass");
      objectsAppearedDuringPass =
          new StatInt(StatKind.SUM, pObjectsDescription + " appeared during pass");
      objectsRemainedAfterPass =
          new StatInt(StatKind.SUM, pObjectsDescription + " remained after pass");
      objectsAppearedAfterPass =
          new StatInt(StatKind.SUM, pObjectsDescription + " appeared just after pass");
      objectsForNextPass = new StatInt(StatKind.SUM, pObjectsDescription + " to mutate next time");
    }
    @Override
    public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
      StatisticsWriter.writingStatisticsTo(pOut)
          .beginLevel()
          .put(getName(), "")
          .put(rounds)
          .put(rollbacks)
          .putIfUpdatedAtLeastOnce(objectsBeforePass)
          .putIfUpdatedAtLeastOnce(objectsAppearedDuringPass)
          .putIfUpdatedAtLeastOnce(objectsRemainedAfterPass)
          .putIfUpdatedAtLeastOnce(objectsAppearedAfterPass)
          .putIfUpdatedAtLeastOnce(objectsForNextPass)
          .endLevel();
    }
    @Override
    public @Nullable String getName() {
      return strategyClassName;
    }
  }

  public GenericCFAMutationStrategy(
      LogManager pLogger, int pRate, boolean ptryAllAtFirst, String pObjectsDescription) {
    super(pLogger);
    assert pRate >= 0;
    rate = pRate;
    tryAllAtFirst = ptryAllAtFirst;
    depth = tryAllAtFirst ? 0 : 1;
    objectsDescription = pObjectsDescription;
    stats = new OnePassMutationStatistics(this.getClass().getSimpleName(), pObjectsDescription);
  }

  protected abstract Collection<ObjectKey> getAllObjects(ParseResult pParseResult);

  protected Collection<ObjectKey> getObjects(ParseResult pParseResult, int count) {
    List<ObjectKey> result = new ArrayList<>();

    int found = 0;
    for (ObjectKey object : getAllObjects(pParseResult)) {
      if (!canRemove(pParseResult, object)) {
        continue;
      }
      //      for (ObjectKey alreadyChosen : result) {
      //        if (!canRemoveInSameRound(object, alreadyChosen)) {
      //          continue;
      //        }
      //      }

      result.add(object);

      if (++found >= count) {
        break;
      }
    }

    return result;
  }

  protected boolean canRemove(
      @SuppressWarnings("unused") ParseResult pParseResult, ObjectKey pObject) {
    return !previousMutations.contains(pObject);
  }

  protected abstract RollbackInfo getRollbackInfo(ParseResult pParseResult, ObjectKey pObject);

  protected abstract void removeObject(ParseResult pParseResult, ObjectKey pObject);

  protected abstract void returnObject(ParseResult pParseResult, RollbackInfo pRollbackInfo);

  @SuppressWarnings("unchecked")
  @Override
  public boolean mutate(ParseResult pParseResult) {
    currentMutation.clear();

    if (stats.rounds.getValue() == 0 && !initLevel(pParseResult)) {
      return false;
    }
    if (batchNum == batchCount && !nextLevel(pParseResult)) {
      return false;
    }

    ImmutableCollection<ObjectKey> chosenObjects = nextBatch(pParseResult);
    if (chosenObjects.isEmpty() && !nextLevel(pParseResult)) {
        return false;
    } else if (chosenObjects.isEmpty()) {
      chosenObjects = nextBatch(pParseResult);
      if (chosenObjects.isEmpty()) {
        countRemained(pParseResult);
        return false;
      }
    }

    stats.rounds.inc();

    final StringBuilder sb = new StringBuilder();
    sb.append("Round ").append(stats.rounds.getValue()).append(". ");
    sb.append("Depth ").append(depth).append(", batch ").append(batchNum).append(". ");
    sb.append("Removing ").append(chosenObjects.size()).append(" ").append(objectsDescription);
    if (exportObjectsEveryRound) {
      sb.append(".\n");
    }
    logger.log(Level.INFO, sb.toString());

    for (ObjectKey object : chosenObjects) {
      if (!objectsBefore.contains(object)) {
        objectsAppeared.add(object);
      }
      RollbackInfo ri = getRollbackInfo(pParseResult, object);
      if (exportObjectsEveryRound) {
        sb.append("removing ").append(object).append("\n");
        sb.append("(rollback info: ").append(ri).append(")\n");
      }
      currentMutation.push(ri);
      removeObject(pParseResult, object);
    }
    previousMutations.addAll(chosenObjects);

    if (exportObjectsEveryRound) {
      try {
        try (Writer writer =
            IO.openOutputFile(exportFile, Charset.defaultCharset(), StandardOpenOption.APPEND)) {
          writer.write(sb.toString());
        }
      } catch (IOException e) {
        logger.logUserException(Level.WARNING, e, "Could not write info from strategy.");
        assert false;
      }
    }

    return true;
  }

  private ImmutableCollection<ObjectKey> nextBatch(ParseResult pParseResult) {
    batchNum++;
    return ImmutableList.copyOf(getObjects(pParseResult, batchSize));
  }

  @SuppressWarnings("unchecked")
  private boolean initLevel(ParseResult pParseResult) {
    batchCount = tryAllAtFirst ? 1 : rate;
    objectsBefore = getAllObjects(pParseResult);
    batchSize = objectsBefore.size();
    if (batchSize == 0) {
      return false;
    }
    stats.objectsBeforePass.setNextValue(batchSize);
    if (!tryAllAtFirst) {
      batchSize = (batchSize - 1) / rate + 1;
    }
    logger.logf(Level.INFO, "batchsize init %d at depth %d", batchSize, depth);
    return true;
  }

  private boolean nextLevel(ParseResult pParseResult) {
    if (batchSize <= 1) {
      countRemained(pParseResult);
      return false;
    }

    depth++;
    batchCount = batchCount * rate;
    batchSize = (batchSize - 1) / rate + 1;
    batchNum = 0;

    logger.logf(Level.INFO, "previous mutations was %d", previousMutations.size());
    previousMutations.clear();
    logger.logf(Level.INFO, "batch size updated %d x %d at depth %d", batchSize, batchCount, depth);
    return true;
  }

  private void countRemained(ParseResult pParseResult) {
    int rem = 0;
    int app = 0;
    objectsAfter = getAllObjects(pParseResult);
    for (ObjectKey o : objectsAfter) {
      if (objectsBefore.contains(o)) {
        logger.logf(Level.INFO, "remained after pass: %s", o);
        rem++;
      } else {
        logger.logf(Level.INFO, "appeared after pass: %s", o);
        objectsAppeared.add(o);
        app++;
      }
    }
    stats.objectsRemainedAfterPass.setNextValue(rem + app);
    if (app > 0) {
      stats.objectsAppearedAfterPass.setNextValue(app);
    }
  }

  @Override
  public void rollback(ParseResult pParseResult) {
    stats.rollbacks.inc();

    final StringBuilder sb = new StringBuilder();
    sb.append("Round ").append(stats.rounds.getValue()).append(". ");
    sb.append("Rollback ").append(stats.rollbacks.getValue()).append(". ");
    sb.append("Returning ").append(currentMutation.size()).append(" ").append(objectsDescription);
    if (exportObjectsEveryRound) {
      sb.append(".\n");
    }
    logger.log(Level.INFO, sb.toString());

    Iterator<RollbackInfo> it = currentMutation.iterator();
    while (it.hasNext()) {
      final RollbackInfo ri = it.next();
      if (exportObjectsEveryRound) {
        sb.append("returning ").append(ri).append("\n");
      }
      returnObject(pParseResult, ri);
    }

    if (exportObjectsEveryRound) {
      try {
        try (Writer writer =
            IO.openOutputFile(exportFile, Charset.defaultCharset(), StandardOpenOption.APPEND)) {
          writer.write(sb.toString());
        }
      } catch (IOException e) {
        logger.logUserException(Level.WARNING, e, "Could not write info from strategy.");
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void makeAftermath(ParseResult pParseResult) {
    Collection<ObjectKey> objects = getAllObjects(pParseResult);
    objects.removeIf(o -> objectsAfter.contains(o) && !objectsAppeared.contains(o));
    stats.objectsForNextPass.setNextValue(objects.size());
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "(" + rate + ")";
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    stats = new OnePassMutationStatistics(this.getClass().getSimpleName(), objectsDescription);
    batchSize = -1;
    batchNum = -1;
    batchCount = -1;
    depth = tryAllAtFirst ? 0 : 1;
  }
}
