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

import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public abstract class GenericCFAMutationStrategy<ObjectKey, RollbackInfo>
    extends AbstractCFAMutationStrategy {

  private Collection<ObjectKey> objectsBefore;

  private final Deque<ObjectKey> currentMutation = new ArrayDeque<>();
  private final Deque<RollbackInfo> rollbackInfos = new ArrayDeque<>();
  private final Set<ObjectKey> previousPasses = new HashSet<>();
  private final Set<ObjectKey> previousMutations = new HashSet<>();
  private int rate = 0;
  private int depth = -1;
  private int levelSize = -1;
  private int batchNum = -1;
  private int batchCount = -1;
  private int batchSize = -1;
  private final boolean tryAllAtFirst;
  private final String objectsDescription;
  private GenericStatistics stats;
  private int pass;

  private enum State {
    NewLevel,
    RemoveComplement,
    RemoveComplementNoRollback,
    RemoveDelta,
    RemoveDeltaNoRollback
  }

  private State state = State.NewLevel;

  protected static class GenericStatistics extends AbstractMutationStatistics {
    // cfa objects for strategy to deal with
    protected final StatInt objectsBeforePass;
    // cfa objects remained because they can't be mutated out or because strategy ran out of rounds
    protected final StatCounter objectsAppearedDuringPass;
    // cfa objects remained because they can't be mutated out or because strategy ran out of rounds
    protected final StatInt objectsRemainedAfterPass;
    protected final StatCounter objectsAppearedAfterPass;
    // cfa objects to mutate in last seen cfa (appeared because of other strategies)
    protected final StatInt objectsForNextPass;
    private final String strategyClassName;

    public GenericStatistics(String pName, String pObjectsDescription) {
      strategyClassName = pName;
      objectsBeforePass = new StatInt(StatKind.SUM, pObjectsDescription + " found before pass");
      objectsAppearedDuringPass = new StatCounter(pObjectsDescription + " appeared during pass");
      objectsRemainedAfterPass =
          new StatInt(StatKind.SUM, pObjectsDescription + " remained after pass");
      objectsAppearedAfterPass = new StatCounter(pObjectsDescription + " appeared just after pass");
      objectsForNextPass = new StatInt(StatKind.SUM, pObjectsDescription + " to mutate next time");
    }

    @Override
    public void printStatistics(PrintStream pOut, int indentLevel) {
      StatisticsWriter.writingStatisticsTo(pOut)
          .withLevel(indentLevel)
          .putIf(indentLevel > 0, getName(), "")
          .putIfUpdatedAtLeastOnce(rounds)
          .putIf(rounds.getValue() > 0, rollbacks)
          .beginLevel()
          .putIfUpdatedAtLeastOnce(objectsBeforePass)
          .putIfUpdatedAtLeastOnce(objectsAppearedDuringPass)
          .putIfUpdatedAtLeastOnce(objectsAppearedAfterPass)
          .putIfUpdatedAtLeastOnce(objectsRemainedAfterPass)
          .putIfUpdatedAtLeastOnce(objectsForNextPass)
          .endLevel();
    }
    @Override
    public @Nullable String getName() {
      return strategyClassName;
    }
  }

  public GenericCFAMutationStrategy(
      LogManager pLogger, int pStartRate, String pObjectsDescription) {
    super(pLogger);
    assert pStartRate >= 0;
    rate = pStartRate;
    tryAllAtFirst = pStartRate == 1;
    objectsDescription = pObjectsDescription;
    stats = new GenericStatistics(this.getClass().getSimpleName(), pObjectsDescription);
    pass = 1;
  }

  protected abstract Collection<ObjectKey> getAllObjects(ParseResult pParseResult);

  protected Collection<ObjectKey> getObjects(ParseResult pParseResult, int maxLimit) {
    List<ObjectKey> result = new ArrayList<>();

    for (ObjectKey object : getAllObjects(pParseResult)) {
      if (alreadyTried(object)) {
        continue;
      }
      //      for (ObjectKey alreadyChosen : result) {
      //        if (!canRemoveInSameRound(object, alreadyChosen)) {
      //          continue;
      //        }
      //      }

      result.add(object);

      if (result.size() >= maxLimit) {
        break;
      }
    }

    return result;
  }

  protected boolean alreadyTried(ObjectKey pObject) {
    return previousPasses.contains(pObject) || previousMutations.contains(pObject);
  }

  protected abstract RollbackInfo getRollbackInfo(ParseResult pParseResult, ObjectKey pObject);

  protected abstract void removeObject(ParseResult pParseResult, ObjectKey pObject);

  protected abstract void returnObject(ParseResult pParseResult, RollbackInfo pRollbackInfo);

  @SuppressWarnings("unchecked")
  @Override
  public boolean mutate(ParseResult pParseResult) {
    currentMutation.clear();

    switch (state) {
      case NewLevel:
        if (!setLevel(pParseResult)) {
          return false;
        }
        if (rate <= 2) {
          state = State.RemoveDelta;
          return mutate(pParseResult);
        }
        state = State.RemoveComplement;
        // $FALL-THROUGH$

      case RemoveComplement:
        if (batchNum++ < batchCount) {
          currentMutation.addAll(getObjects(pParseResult, levelSize - batchSize));
          logger.logf(
              Level.FINE,
              "removing complement %d/%d: %d %s",
              batchNum,
              batchCount,
              currentMutation.size(),
              objectsDescription);
          if (!currentMutation.isEmpty()) {
            state = State.RemoveComplementNoRollback;
            break;
          }
        }
        batchNum = 0;
        state = State.RemoveDelta;
        previousMutations.clear();
        logger.logf(Level.FINE, "switching to deltas");
        // $FALL-THROUGH$

      case RemoveDelta:
        if (batchNum++ < batchCount) {
          currentMutation.addAll(getObjects(pParseResult, batchSize));
          logger.logf(
              Level.INFO,
              "removing delta %d: %d %s",
              batchNum,
              currentMutation.size(),
              objectsDescription);
          if (!currentMutation.isEmpty()) {
            previousMutations.addAll(currentMutation);
            state = State.RemoveDeltaNoRollback;
            break;
          }
        }
        state = State.NewLevel;
        return mutate(pParseResult);

      case RemoveComplementNoRollback:
        logger.logf(Level.FINE, "complement %d/%d was removed successfully", batchNum, batchCount);
        rate = 2;
        state = State.NewLevel;
        return mutate(pParseResult);

      case RemoveDeltaNoRollback:
        logger.logf(Level.FINE, "delta %d/%d was removed successfully", batchNum, batchCount);
        state = --rate < 2 ? State.NewLevel : State.RemoveDelta;
        return mutate(pParseResult);
    }

    stats.rounds.inc();
    rollbackInfos.clear();
    for (ObjectKey object : currentMutation) {
      final RollbackInfo ri = getRollbackInfo(pParseResult, object);
      rollbackInfos.push(ri);
      //      logger.logf(Level.INFO, "removing %s with ri %s", object, ri);
      removeObject(pParseResult, object);
    }

    return true;
  }

  @SuppressWarnings("unchecked")
  private boolean initLevel(ParseResult pParseResult) {
    if (rate < 1) {
      throw new UnsupportedOperationException("Rate must be > 0 but is " + rate);
    }
    depth = tryAllAtFirst ? 0 : 1;

    objectsBefore = getAllObjects(pParseResult);
    objectsBefore.removeAll(previousPasses);
    levelSize = objectsBefore.size();
    if (levelSize == 0) {
      return false;
    }

    batchSize = (levelSize - 1) / rate + 1;
    stats.objectsBeforePass.setNextValue(levelSize);
    return true;
  }

  private boolean setLevel(ParseResult pParseResult) {
    if (depth == -1) {
      return initLevel(pParseResult);
    }

    previousMutations.clear();
    depth++;
    Collection<ObjectKey> level = getAllObjects(pParseResult);
    levelSize = level.size();
    for (ObjectKey o : level) {
      if (!objectsBefore.contains(o)) {
        stats.objectsAppearedDuringPass.inc();
      }
    }

    if (batchSize <= 1 || levelSize == 0) {
      countRemained(level);
      return false;
    }

    if (rate < 2) {
      rate = 2;
    } else {
      rate *= 2;
    }

    if (rate >= levelSize) {
      batchSize = 1;
      rate = levelSize;
    } else {
      batchSize = (levelSize - 1) / rate + 1;
    }
    batchNum = 0;
    batchCount = rate;

    logger.logf(Level.FINE, "new level: %d deltas of size %d at depth %d", rate, batchSize, depth);
    return true;
  }

  private void countRemained(Collection<ObjectKey> objectsAfter) {
    for (ObjectKey o : objectsAfter) {
      if (!previousPasses.contains(o)) {
        if (objectsBefore.contains(o)) {
          logger.logf(Level.FINE, "remained after pass: %s", o);
          previousPasses.add(o);
        } else {
          logger.logf(Level.FINE, "appeared after pass: %s", o);
          stats.objectsAppearedAfterPass.inc();
        }
      }
    }
    stats.objectsRemainedAfterPass.setNextValue(objectsAfter.size());
  }

  @Override
  public void rollback(ParseResult pParseResult) {
    stats.rollbacks.inc();

    switch (state) {
      case RemoveComplementNoRollback:
        state = State.RemoveComplement;
        previousMutations.clear();
        int count = 0;
        for (final ObjectKey object : currentMutation) {
          if (++count > batchSize) {
            break;
          }
          previousMutations.add(object);
        }
        break;
      case RemoveDeltaNoRollback:
        state = State.RemoveDelta;
        break;
      default:
        throw new UnsupportedOperationException("" + state);
    }

    for (final RollbackInfo ri : rollbackInfos) {
      //      logger.logf(Level.INFO, "returning ri %s", ri);
      returnObject(pParseResult, ri);
    }

  }

  @SuppressWarnings("unchecked")
  @Override
  public void makeAftermath(ParseResult pParseResult) {
    Collection<ObjectKey> objects = getAllObjects(pParseResult);
    objects.removeIf(o -> previousPasses.contains(o));
    stats.objectsForNextPass.setNextValue(objects.size());
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "(" + pass + " pass, depth " + depth + ")";
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    stats =
        new GenericStatistics(
            this.getClass().getSimpleName() + " " + ++pass + " pass", objectsDescription);
    levelSize = batchSize = batchNum = batchCount = depth = -1;
    rate = tryAllAtFirst ? 1 : 2;
  }
}
