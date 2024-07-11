// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.refinement;

import static org.sosy_lab.common.collect.Collections3.transformedImmutableListCopy;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

public class PathPairIterator
    extends GenericPairIterator<Pair<UsageInfo, UsageInfo>, ExtendedARGPath> {

  private final Set<List<Integer>> refinedStates = new HashSet<>();
  private PathRestorator subgraphComputer;
  private final Set<Integer> skippedStates;
  // Not only refined, but also not checked
  private final Map<UsageInfo, ExtendedARGPath> calculatedPaths = new HashMap<>();

  private final StatInt refinedStateNum =
      new StatInt(StatKind.SUM, "Number of stored refined states");
  private final StatCounter skippedCounter = new StatCounter("Number of skipped usages");
  private final StatCounter skippedStatesCounter = new StatCounter("Number of skipped states");

  private final Function<ARGState, Integer> idExtractor;

  private List<UsageInfo> usages = new ArrayList<>(2);
  private List<ARGPathIterator> iterators = new ArrayList<>(2);

  public PathPairIterator(
      ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper,
      PathRestorator pComputer,
      Function<ARGState, Integer> pExtractor,
      ShutdownNotifier pNotifier,
      int pInterationLimit) {
    super(pWrapper, pNotifier, pInterationLimit);
    subgraphComputer = pComputer;
    skippedStates = new TreeSet<>();
    idExtractor = pExtractor;
  }

  @Override
  protected void init(Pair<UsageInfo, UsageInfo> pInput) {
    super.init(pInput);
    usages.clear();
    usages.add(pInput.getFirst());
    usages.add(pInput.getSecond());

    iterators.clear();
    iterators.add(
        subgraphComputer.iterator(
            (ARGState) pInput.getFirst().getKeyState(), pInput.getFirst().getExpandedStack()));
    iterators.add(
        subgraphComputer.iterator(
            (ARGState) pInput.getSecond().getKeyState(), pInput.getSecond().getExpandedStack()));
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void finishIteration(
      Pair<ExtendedARGPath, ExtendedARGPath> pathPair, RefinementResult wrapperResult) {
    ExtendedARGPath firstExtendedPath = pathPair.getFirst();
    ExtendedARGPath secondExtendedPath = pathPair.getSecond();

    List<ARGState> firstAffectedStates = null;
    List<ARGState> secondAffectedStates = null;

    if (wrapperResult.isFalse()) {
      Object predicateInfo = wrapperResult.getInfo(PredicateRefinerAdapter.class);
      if (predicateInfo instanceof List) {
        // affectedStates may be null, if the path was refined somewhen before

        // A feature of GenericSinglePathRefiner: if one path is false, the second one is not
        // refined
        if (firstExtendedPath.isUnreachable()) {
          // This one is false
          firstAffectedStates = (List<ARGState>) predicateInfo;
        } else {
          // The second one must be
          Preconditions.checkArgument(
              secondExtendedPath.isUnreachable(),
              "Either the first path, or the second one must be unreachable here");
          secondAffectedStates = (List<ARGState>) predicateInfo;
        }
      }

      predicateInfo = wrapperResult.getInfo(LockRefiner.class);
      if (predicateInfo instanceof Pair) {
        assert firstAffectedStates == null;
        assert secondAffectedStates == null;
        Pair<List<ARGState>, List<ARGState>> lockInfo =
            (Pair<List<ARGState>, List<ARGState>>) predicateInfo;

        firstAffectedStates = lockInfo.getFirst();
        secondAffectedStates = lockInfo.getSecond();
      }

      predicateInfo = wrapperResult.getInfo(SharedRefiner.class);
      if (predicateInfo instanceof List) {
        assert firstAffectedStates == null;
        assert secondAffectedStates == null;
        // affectedStates may be null, if the path was refined somewhen before

        // A feature of GenericSinglePathRefiner: if one path is false, the second one is not
        // refined
        if (firstExtendedPath.isUnreachable()) {
          // This one is false
          firstAffectedStates = (List<ARGState>) predicateInfo;
        } else {
          // The second one must be
          Preconditions.checkArgument(
              secondExtendedPath.isUnreachable(),
              "Either the first path, or the second one must be unreachable here");
          secondAffectedStates = (List<ARGState>) predicateInfo;
        }
      }

      if (firstExtendedPath.isUnreachable()) {
        firstItem = null;
        handleAffectedStates(firstAffectedStates);
        removeAsUnreachable(firstExtendedPath);
      } else {
        addAsReachable(firstExtendedPath);
      }
      if (secondExtendedPath.isUnreachable() && secondExtendedPath != firstExtendedPath) {
        handleAffectedStates(secondAffectedStates);
        removeAsUnreachable(secondExtendedPath);
      } else {
        if (firstExtendedPath.getUsageInfo() != secondExtendedPath.getUsageInfo()) {
          addAsReachable(secondExtendedPath);
        }
      }
    }
  }

  private void removeAsUnreachable(ExtendedARGPath pPath) {
    UsageInfo key = pPath.getUsageInfo();
    if (calculatedPaths.containsKey(key)) {
      calculatedPaths.remove(key);
    }
  }

  private void addAsReachable(ExtendedARGPath pPath) {
    UsageInfo key = pPath.getUsageInfo();
    if (calculatedPaths.containsKey(key)) {
      assert calculatedPaths.get(key) == pPath;
    } else {
      calculatedPaths.put(key, pPath);
    }
  }

  @Override
  protected void finish(Pair<UsageInfo, UsageInfo> pInput, RefinementResult pResult) {
    if (pResult.isFalse()) {
      List<UsageInfo> unreacheableUsages = new ArrayList<>();
      if (!calculatedPaths.containsKey(pInput.getFirst())) {
        unreacheableUsages.add(pInput.getFirst());
      }
      if (!calculatedPaths.containsKey(pInput.getSecond())) {
        unreacheableUsages.add(pInput.getSecond());
      }

      pResult.addInfo(this.getClass(), unreacheableUsages);
    }
  }

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> callerClass) {
    super.handleFinishSignal(callerClass);
    if (callerClass.equals(IdentifierIterator.class)) {
      // Refinement iteration finishes
      refinedStates.clear();
      usages.clear();
      iterators.clear();
    } else if (callerClass.equals(PointIterator.class)) {
      skippedStates.clear();
      calculatedPaths.clear();
    }
  }

  @Override
  protected ExtendedARGPath getNextIteration0(int pId) {
    ARGPath currentPath;

    if (calculatedPaths.containsKey(usages.get(pId))) {
      ExtendedARGPath path = calculatedPaths.get(usages.get(pId));
      if (!isRepeated(path)) {
        return path;
      }
      // Means, that the path was not refined, when stored in calculatedPaths
      calculatedPaths.remove(usages.get(pId));
    }

    AbstractState keyState = usages.get(pId).getKeyState();
    if (skippedStates.contains(idExtractor.apply((ARGState) keyState))) {
      skippedStatesCounter.inc();
      return null;
    }
    // try to compute more paths
    ARGPathIterator pathIterator = iterators.get(pId);
    currentPath = pathIterator.nextPath(refinedStates);

    if (currentPath == null) {
      // no path to iterate, finishing
      skippedCounter.inc();
      skippedStates.add(idExtractor.apply((ARGState) usages.get(pId).getKeyState()));
      return null;
    }

    ExtendedARGPath result = new ExtendedARGPath(currentPath, usages.get(pId));
    // Not add result now, only after refinement
    return result;
  }

  private boolean isRepeated(ARGPath path) {
    List<Integer> changedStateNumbers =
        transformedImmutableListCopy(path.asStatesList(), idExtractor);
    for (List<Integer> nums : refinedStates) {
      if (changedStateNumbers.containsAll(nums)) {
        return true;
      }
    }
    return false;
  }

  private void handleAffectedStates(List<ARGState> affectedStates) {
    if (affectedStates != null) {
      List<Integer> changedStateNumbers = transformedImmutableListCopy(affectedStates, idExtractor);
      assert !changedStateNumbers.isEmpty();
      Iterator<List<Integer>> numIterator = refinedStates.iterator();
      while (numIterator.hasNext()) {
        List<Integer> nums = numIterator.next();
        if (nums.containsAll(changedStateNumbers)) {
          numIterator.remove();
        }
        assert !changedStateNumbers.containsAll(nums);
      }
      refinedStates.add(changedStateNumbers);
      refinedStateNum.setNextValue(changedStateNumbers.size());
    }
  }

  @Override
  protected void resetSecondIterationFor() {
    iterators.set(
        1,
        subgraphComputer.iterator(
            (ARGState) usages.get(1).getKeyState(), usages.get(1).getExpandedStack()));
  }

  @Override
  protected void printDetailedStatistics(StatisticsWriter pOut) {
    super.printDetailedStatistics(pOut);
    pOut.put(skippedCounter).put(skippedStatesCounter).put(refinedStateNum).spacer();
    subgraphComputer.printStatistics(pOut);
  }

  @Override
  protected String getName() {
    return "PathPairIterator";
  }
}
