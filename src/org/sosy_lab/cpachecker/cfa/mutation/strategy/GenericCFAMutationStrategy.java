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

import com.google.common.collect.ImmutableSet;
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

  protected Level logObjects = Level.FINE; // for "removing object" and "returning object" messages
  protected Level logDetails = Level.FINER; // for more mutation details

  private Collection<ObjectKey> objectsBefore;

  // info to return them into CFA
  private final Deque<RollbackInfo> rollbackInfos = new ArrayDeque<>();
  // all objects of current level divided into deltas
  // private List<Collection<ObjectKey>> deltaList;
  // this collection helps to divide objects into deltas
  private final Collection<ObjectKey> previousMutations = new HashSet<>();
  // the objects that were tried in previous passes
  // TODO and so what?.. it can be useful to try in new context...
  private final Set<ObjectKey> previousPasses = new HashSet<>();
  private final Set<ImmutableSet<ObjectKey>> triedSets = new HashSet<>();

  private int rate = 0, iteration = 0, deltaNum, maxDeltaNum, deltaSize;
  private final String objectsDescription;
  private GenericStatistics stats;

  private enum State {
    DIVIDE,
    REMOVE_COMPLEMENT,
    REMOVE_DELTA
  }

  private State state = State.DIVIDE;
  private boolean wasRollback;

  private List<Collection<ObjectKey>> complementList;

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
        deltaRollbacks = 0,
        sameRounds = 0;

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
      w.ifTrue(rounds.getValue() > 0).put(rounds).put(rollbacks);
      w.ifTrue(complementRounds > 0)
          .put("on complements", "" + complementRounds + "/" + complementRollbacks);
      w.ifTrue(deltaRounds > 0).put("on deltas", "" + deltaRounds + "/" + deltaRollbacks);
      w.putIf(sameRounds > 0, "found same set to remove", sameRounds);
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
    init();
  }

  private void init() {
    stats =
        new GenericStatistics(
            this.getClass().getSimpleName() + " " + ++iteration + " pass", objectsDescription);
    state = State.DIVIDE;
    rate = 0;
    complementList = null;
    rollbackInfos.clear();
    triedSets.clear();
    previousMutations.clear();
  }

  protected abstract Collection<ObjectKey> getAllObjects(ParseResult pParseResult);

  protected Collection<ObjectKey> getObjects(ParseResult pParseResult, int maxLimit) {
    // TODO return 0 objects if 0 requested
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
  // 1. Divide objects in CFA into parts, "deltas".
  //    A complement is the set of all objects without the corresponding delta.
  // 2. Remove a complement, or to put it the other way, let one delta remain.
  //    If the bug remains, we have to divide remained objects next time in new deltas.
  //    If the bug disappears, restore the complement and remove another one.
  // 3. After trying all complements, try deltas the same way.
  // 4. After trying all complements and all deltas divide the remaining in smaller deltas
  //    and repeat the algorithm.
  // Algorithm ends when there are no objects or each delta consists of one object.
  @Override
  public boolean mutate(ParseResult pParseResult) {
    while (true) {
      switch (state) {
        case DIVIDE:
          Collection<ObjectKey> toRemove1 = divideAndGet(pParseResult);
          if (toRemove1 == null) {
            return false;
          }
          removeObjects(pParseResult, toRemove1);
          return true;

        case REMOVE_COMPLEMENT:
          stats.complementRounds++;
          if (wasRollback) {
            stats.complementRollbacks++;
            Collection<ObjectKey> toRemove = getComplement();
            if (toRemove == null) {
              deltaNum = 0;
              logger.logf(Level.INFO, "switching to deltas");
              state = State.REMOVE_DELTA;
              break;
            }
            removeObjects(pParseResult, toRemove);
            return true;
          }
          logger.logf(Level.FINE, "complement %d/%d was removed successfully", deltaNum, maxDeltaNum);
          rate = 2;
          state = State.DIVIDE;
          break;

        case REMOVE_DELTA:
          stats.deltaRounds++;
          if (wasRollback) {
            stats.deltaRollbacks++;
            Collection<ObjectKey> toRemove = getDelta(pParseResult);
            if (toRemove == null) {
              state = State.DIVIDE;
              break;
            }
            removeObjects(pParseResult, toRemove);
            return true;
          }
          logger.logf(Level.FINE, "delta %d/%d was removed successfully", deltaNum, maxDeltaNum);
          if (--rate < 2) {
            state = State.DIVIDE;
            break;
          }
          Collection<ObjectKey> toRemove = getDelta(pParseResult);
          if (toRemove == null) {
            state = State.DIVIDE;
            break;
          }
          removeObjects(pParseResult, toRemove);
          return true;
      }
    }
  }

  // no need in parse result as complements were already prepared
  private Collection<ObjectKey> getComplement() {
    if (deltaNum >= maxDeltaNum) {
      return null; // switch to deltas
    }

    Collection<ObjectKey> toRemove = complementList.get(deltaNum);
    deltaNum++;

    logger.logf(
        Level.INFO,
        "removing complement %d/%d: %d %s",
        deltaNum,
        maxDeltaNum,
        toRemove.size(),
        objectsDescription);
    return checkToRemove(toRemove);
  }

  private Collection<ObjectKey> getDelta(ParseResult pParseResult) {
    if (deltaNum >= maxDeltaNum) {
      return null;
    }

    Collection<ObjectKey> toRemove = getObjects(pParseResult, deltaSize);
    deltaNum++;

    logger.logf(
        Level.INFO,
        "removing delta %d/%d: %d %s (%d requested)",
        deltaNum,
        maxDeltaNum,
        toRemove.size(),
        objectsDescription,
        deltaSize);

    previousMutations.addAll(toRemove);

    return checkToRemove(toRemove);
  }

  private Collection<ObjectKey> checkToRemove(Collection<ObjectKey> pToRemove) {
    ImmutableSet<ObjectKey> trySet = ImmutableSet.copyOf(pToRemove);
    if (triedSets.contains(trySet)) {
      logger.log(Level.FINE, "Already have rollbacked");
      stats.sameRounds++;
      return null;
    } else {
      triedSets.add(trySet);
      return pToRemove;
    }
  }

  private void removeObjects(ParseResult pParseResult, Collection<ObjectKey> pToRemove) {
    stats.rounds.inc();
    rollbackInfos.clear();

    for (ObjectKey object : pToRemove) {
      rollbackInfos.push(removeObject(pParseResult, object));
      //      logger.logf(Level.INFO, "removing %s with ri %s", object, ri);
    }
    wasRollback = false;
  }

  private Collection<ObjectKey> divideAndGet(ParseResult pParseResult) {
    previousMutations.clear();

    Collection<ObjectKey> objects = getAllObjects(pParseResult);
    objects.removeAll(previousPasses);
    int totalObjectsNum = objects.size();
    if (totalObjectsNum == 0) {
      return null;
    }

    if (rate == 0) { // it is a new iteration
      maxDeltaNum = rate = 1;
      objectsBefore = objects;
      stats.objectsBeforePass.setNextValue(totalObjectsNum);
      deltaSize = totalObjectsNum;

    } else { // it is just a new level
      // objects.forEach(o -> {if (!objectsBefore.contains(o)) TODO
      // {stats.objectsAppearedDuringPass.inc();}});

      if (deltaSize <= 1) {
        // we have already tried the smallest deltas, finish this pass
        countRemained(objects);
        return null;
      }

      // make deltas twice smaller
      rate *= 2;
      if (rate >= totalObjectsNum) {
        deltaSize = 1;
        rate = totalObjectsNum;
      } else {
        deltaSize = (totalObjectsNum - 1) / rate + 1;
      }
    }

    deltaNum = 0;
    maxDeltaNum = rate;

    int complementSize = totalObjectsNum - deltaSize;
    complementList = new ArrayList<>();
    int maxObjs = getObjects(pParseResult, totalObjectsNum + deltaSize).size();
    assert maxObjs <= totalObjectsNum;
    // skip complements because of the size
    boolean skipComplements = false; // maxObjs < (rate - 2) * deltaSize;
    logger.logf(
        Level.FINE,
        "new level:%s %d deltas of size %d",
        skipComplements ? " complements skipped." : "",
        rate,
        deltaSize);

    // mutate
    // if there should be no complements
    if (rate <= 2 || !useComplements || skipComplements) {
      state = State.REMOVE_DELTA;
      return getDelta(pParseResult);
    }
    // else prepare them inbefore
    prepareComplements(pParseResult, complementSize);
    state = State.REMOVE_COMPLEMENT;
    return getComplement();
  }

  private void prepareComplements(ParseResult pParseResult, int complementSize) {
    // divide objects into complements
    for (int d = 0; d < rate; d++) {
      Collection<ObjectKey> complement = getObjects(pParseResult, complementSize);
      logger.logf(
          Level.INFO,
          "Got %d/%d complement, %d objects (requested %d).",
          d + 1,
          rate,
          complement.size(),
          complementSize);
      if (complement.size() == 0) {
        rate = d;
        break;
      }
      complementList.add(complement);

      Set<ObjectKey> prevDelta = ImmutableSet.copyOf(previousMutations);
      int count = 0;
      for (ObjectKey o : complement) {
        if (previousMutations.add(o) && ++count == deltaSize) {
          break;
        }
      }
      previousMutations.removeAll(prevDelta);
    }
    previousMutations.clear();
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
    for (final RollbackInfo ri : rollbackInfos) {
      returnObject(pParseResult, ri);
    }
  }

  @Override
  public void makeAftermath(ParseResult pParseResult) {
    Collection<ObjectKey> objects = getAllObjects(pParseResult);
    objects.removeAll(previousPasses);
    stats.objectsForNextPass.setNextValue(objects.size());
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + " (" + iteration + " pass)";
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
    init();
  }
}
