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
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ParseResult;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

@Options(prefix = "cfa.mutations")
public abstract class GenericCFAMutationStrategy<ObjectKey, RollbackInfo>
    extends AbstractCFAMutationStrategy {
  @Option(
      secure = true,
      name = "useComplements",
      description = "whether to try to remove complements")
  private boolean useComplements = false;

  private Collection<ObjectKey> objectsBefore;

  // the objects that are removed by last mutation
  private final Deque<ObjectKey> currentMutation = new ArrayDeque<>();
  // info to return them into CFA
  private final Deque<RollbackInfo> rollbackInfos = new ArrayDeque<>();
  // this collection helps to try new objects as they are not strictly divided into deltas
  private final Set<ObjectKey> previousMutations = new HashSet<>();
  // the objects that were tried in previous passes
  // TODO and so what?.. it can be useful to try in new context...
  private final Set<ObjectKey> previousPasses = new HashSet<>();

  private int rate = 0, pass = 1, levelSize, deltaNum, deltaCount, deltaSize;
  private final String objectsDescription;
  private GenericStatistics stats;

  private enum State {
    NewLevel,
    RemoveComplement,
    RemoveDelta
  }

  private State state = State.NewLevel;
  private boolean wasRollback;

  protected static class GenericStatistics extends AbstractMutationStatistics {
    // cfa objects for strategy to deal with
    protected final StatInt objectsBeforePass;
    // cfa objects remained because they can't be mutated out or because strategy ran out of rounds
    protected final StatCounter objectsAppearedDuringPass;
    // cfa objects remained because they can't be mutated out or because strategy ran out of rounds
    protected final StatInt objectsRemainedAfterPass;
    // protected final StatCounter objectsAppearedAfterPass;
    // cfa objects to mutate in last seen cfa (appeared because of other strategies)
    protected final StatInt objectsForNextPass;
    private final String strategyClassName;

    protected int complementRounds = 0,
        complementRollbacks = 0,
        deltaRounds = 0,
        deltaRollbacks = 0;

    public GenericStatistics(String pName, String pObjectsDescription) {
      strategyClassName = pName;
      objectsBeforePass = new StatInt(StatKind.SUM, pObjectsDescription + " found before pass");
      objectsAppearedDuringPass = new StatCounter(pObjectsDescription + " appeared during pass");
      objectsRemainedAfterPass =
          new StatInt(StatKind.SUM, pObjectsDescription + " remained after pass");
      // objectsAppearedAfterPass = new StatCounter(pObjectsDescription + " appeared just after
      // pass");
      objectsForNextPass = new StatInt(StatKind.SUM, pObjectsDescription + " to mutate next time");
    }

    @Override
    public void printStatistics(PrintStream pOut, int indentLevel) {
      StatisticsWriter w =
          StatisticsWriter.writingStatisticsTo(pOut)
              .withLevel(indentLevel)
              .putIf(indentLevel > 0, getName(), "");
      w.ifTrue(rounds.getValue() > 0)
          .put(rounds)
          .put(rollbacks)
          .putIf(
              complementRounds > 0,
              "on complements",
              "" + complementRounds + "/" + complementRollbacks)
          .put("on deltas", "" + deltaRounds + "/" + deltaRollbacks);
      w.beginLevel()
          .putIfUpdatedAtLeastOnce(objectsBeforePass)
          .putIfUpdatedAtLeastOnce(objectsAppearedDuringPass)
          // .putIfUpdatedAtLeastOnce(objectsAppearedAfterPass)
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
      Configuration pConfig, LogManager pLogger, String pObjectsDescription)
      throws InvalidConfigurationException {
    super(pLogger);
    pConfig.inject(this, GenericCFAMutationStrategy.class);
    objectsDescription = pObjectsDescription;
    stats = new GenericStatistics(this.getClass().getSimpleName(), pObjectsDescription);
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

  protected abstract RollbackInfo removeObject(ParseResult pParseResult, ObjectKey pObject);

  protected abstract void returnObject(ParseResult pParseResult, RollbackInfo pRollbackInfo);

  // ddmin algorithm (see Delta Debugging)
  // 1. "Set level". Divide objects in CFA into parts, "deltas".
  //    A complement is the set of all objects without the corresponding delta.
  // 2. Remove a complement, or too put it the other way, let one delta remain.
  //    If the bug remains, we have to divide remained objects next time in new deltas.
  //    If the bug disappears, restore the complement and remove another one.
  // 3. After trying all complements, try deltas the same way.
  // 4. After trying all complements and all deltas divide the remaining in smaller deltas
  //    and repeat the algorithm.
  // Algorithm ends when there are no objects or each delta consists of one object.
  @SuppressWarnings("unchecked")
  @Override
  public boolean mutate(ParseResult pParseResult) {
    currentMutation.clear();

    switch (state) {
      case NewLevel:
        return setLevelAndMutate(pParseResult);

      case RemoveComplement:
        stats.complementRounds++;
        if (wasRollback) {
          stats.complementRollbacks++;
          return removeComplement(pParseResult);
        }
        logger.logf(Level.FINE, "complement %d/%d was removed successfully", deltaNum, deltaCount);
        rate = 2;
        return setLevelAndMutate(pParseResult);

      case RemoveDelta:
        stats.deltaRounds++;
        if (wasRollback) {
          stats.deltaRollbacks++;
          return removeDelta(pParseResult);
        }
        logger.logf(Level.FINE, "delta %d/%d was removed successfully", deltaNum, deltaCount);
        if (--rate < 2) {
          return setLevelAndMutate(pParseResult);
        } else {
          return removeDelta(pParseResult);
        }
    }

    return mutateEpilogue(pParseResult);
  }

  private boolean removeComplement(ParseResult pParseResult) {
    if (deltaNum++ < deltaCount) {
      currentMutation.addAll(getObjects(pParseResult, levelSize - deltaSize));
      logger.logf(
          Level.FINE,
          "removing complement %d/%d: %d %s",
          deltaNum,
          deltaCount,
          currentMutation.size(),
          objectsDescription);
      if (!currentMutation.isEmpty()) {
        return mutateEpilogue(pParseResult);
      }
    }
    deltaNum = 0;
    state = State.RemoveDelta;
    previousMutations.clear();
    logger.logf(Level.FINE, "switching to deltas");
    return removeDelta(pParseResult);
  }

  private boolean removeDelta(ParseResult pParseResult) {
    // TODO? dont count deltas, just keep going while not isEmpty
    // then deltaCount is not needed (in removeComplement it can be replaced with rate)
    // Actually, a lot of new rounds occurs
    if (deltaNum++ < deltaCount) {
      currentMutation.addAll(getObjects(pParseResult, deltaSize));
      logger.logf(
          Level.INFO,
          "removing delta %d: %d %s",
          deltaNum,
          currentMutation.size(),
          objectsDescription);
      if (!currentMutation.isEmpty()) {
        previousMutations.addAll(currentMutation);
        return mutateEpilogue(pParseResult);
      }
    }
    return setLevelAndMutate(pParseResult);
  }

  private boolean mutateEpilogue(ParseResult pParseResult) {
    stats.rounds.inc();
    rollbackInfos.clear();
    for (ObjectKey object : currentMutation) {
      rollbackInfos.push(removeObject(pParseResult, object));
      //      logger.logf(Level.INFO, "removing %s with ri %s", object, ri);
    }
    wasRollback = false;
    return true;
  }

  private boolean setLevelAndMutate(ParseResult pParseResult) {
    // need to clear on every level and when change from complements to deltas
    previousMutations.clear();

    // set level
    Collection<ObjectKey> level = getAllObjects(pParseResult);
    level.removeAll(previousPasses);
    levelSize = level.size();
    if (levelSize == 0) {
      return false;
    }

    if (rate == 0) { // it is a new pass
      rate = 1;
      objectsBefore = level;
      stats.objectsBeforePass.setNextValue(levelSize);
      deltaSize = (levelSize - 1) / rate + 1;

    } else { // it is just a new level
      level.forEach(
          o -> {
            if (!objectsBefore.contains(o)) {
              stats.objectsAppearedDuringPass.inc();
            }
          });

      if (deltaSize <= 1) {
        // we have already tried the smallest deltas, finish this pass
        countRemained(level);
        return false;
      }

      // make deltas twice smaller
      rate *= 2;
      if (rate >= levelSize) {
        deltaSize = 1;
        rate = levelSize;
      } else {
        deltaSize = (levelSize - 1) / rate + 1;
      }
    }

    deltaNum = 0;
    deltaCount = rate;

    logger.logf(Level.FINE, "new level: %d deltas of size %d", rate, deltaSize);

    // actually mutate
    if (rate <= 2 || !useComplements) {
      state = State.RemoveDelta;
      return removeDelta(pParseResult);
    }
    state = State.RemoveComplement;
    return removeComplement(pParseResult);
  }

  private void countRemained(Collection<ObjectKey> objectsAfter) {
    for (ObjectKey o : objectsAfter) {
      if (objectsBefore.contains(o)) {
        logger.logf(Level.FINE, "stays after pass: %s", o);
        previousPasses.add(o);
      }
    }
    stats.objectsRemainedAfterPass.setNextValue(objectsAfter.size());
  }

  @Override
  public void rollback(ParseResult pParseResult) {
    wasRollback = true;
    stats.rollbacks.inc();

    switch (state) {
      case RemoveComplement:
        previousMutations.clear();
        int count = 0;
        for (final ObjectKey object : currentMutation) {
          if (++count > deltaSize) {
            break;
          }
          previousMutations.add(object);
        }
        break;
      case RemoveDelta:
        break;
      default:
        throw new UnsupportedOperationException(state.toString());
    }

    for (final RollbackInfo ri : rollbackInfos) {
      //      logger.logf(Level.INFO, "returning ri %s", ri);
      returnObject(pParseResult, ri);
    }

  }

  // TODO why makeAftermath and collectStatistics are different?
  @Override
  public void makeAftermath(ParseResult pParseResult) {
    Collection<ObjectKey> objects = getAllObjects(pParseResult);
    objects.removeAll(previousPasses);
    stats.objectsForNextPass.setNextValue(objects.size());
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + " (" + pass + " pass)";
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    stats =
        new GenericStatistics(
            this.getClass().getSimpleName() + " " + ++pass + " pass", objectsDescription);
    state = State.NewLevel;
    rate = 0;
  }
}
