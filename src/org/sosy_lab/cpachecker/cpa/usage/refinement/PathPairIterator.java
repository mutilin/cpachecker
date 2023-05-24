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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo;
import org.sosy_lab.cpachecker.util.Pair;

public class PathPairIterator extends
    GenericPairIterator<Pair<UsageInfo, UsageInfo>, ExtendedARGPath> {

  private final Set<List<Integer>> refinedStates = new HashSet<>();
  private PathRestorator subgraphComputer;
  private final Set<UsageInfo> skippedUsages;

  private final Function<ARGState, Integer> idExtractor;

  private UsageInfo[] usages = new UsageInfo[2];
  private PathIterator[] iterators = new PathIterator[2];
  private boolean[] isFeasiblePath = new boolean[2];

  public PathPairIterator(
      ConfigurableRefinementBlock<Pair<ExtendedARGPath, ExtendedARGPath>> pWrapper,
      PathRestorator pComputer,
      Function<ARGState, Integer> pExtractor,
      ShutdownNotifier pNotifier,
      int pInterationLimit) {
    super(pWrapper, pNotifier, pInterationLimit);
    subgraphComputer = pComputer;
    skippedUsages = new HashSet<>();
    idExtractor = pExtractor;
  }

  @Override
  protected void init(Pair<UsageInfo, UsageInfo> pInput) {
    super.init(pInput);
    usages[0] = pInput.getFirst();
    usages[1] = pInput.getSecond();
    isFeasiblePath[0] = false;
    isFeasiblePath[1] = false;

    iterators[0] =
        subgraphComputer.iterator((ARGState) usages[0].getKeyState(), usages[0].getExpandedStack());
    iterators[1] =
        subgraphComputer.iterator((ARGState) usages[1].getKeyState(), usages[1].getExpandedStack());
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void finishIteration(Pair<ExtendedARGPath, ExtendedARGPath> pathPair, RefinementResult wrapperResult) {
    ExtendedARGPath firstExtendedPath, secondExtendedPath;

    firstExtendedPath = pathPair.getFirst();
    secondExtendedPath = pathPair.getSecond();

    List<ARGState> firstAffectedStates = null;
    List<ARGState> secondAffectedStates = null;

    Object predicateInfo = wrapperResult.getInfo(PredicateRefinerAdapter.class);
    if (predicateInfo instanceof List) {
      //affectedStates may be null, if the path was refined somewhen before

      //A feature of GenericSinglePathRefiner: if one path is false, the second one is not refined
      if (firstExtendedPath.isUnreachable()) {
        //This one is false
        firstAffectedStates = (List<ARGState>)predicateInfo;
      } else {
        // The second one must be
        Preconditions.checkArgument(
            secondExtendedPath.isUnreachable(),
            "Either the first path, or the second one must be unreachable here");
        secondAffectedStates = (List<ARGState>)predicateInfo;
      }
    }

    predicateInfo = wrapperResult.getInfo(LockRefiner.class);
    if (predicateInfo instanceof Pair) {
      Pair<List<ARGState>, List<ARGState>> lockInfo =
          (Pair<List<ARGState>, List<ARGState>>) predicateInfo;

      firstAffectedStates = lockInfo.getFirst();
      secondAffectedStates = lockInfo.getSecond();
    }

    if (firstExtendedPath.isUnreachable()) {
      firstItem = null;
      handleAffectedStates(firstAffectedStates);
    } else {
      isFeasiblePath[0] = true;
    }
    if (secondExtendedPath.isUnreachable() && secondExtendedPath != firstExtendedPath) {
      handleAffectedStates(secondAffectedStates);
    } else {
      isFeasiblePath[1] = true;
    }
  }

  @Override
  protected void finish(Pair<UsageInfo, UsageInfo> pInput, RefinementResult pResult) {
    List<UsageInfo> unreacheableUsages = new ArrayList<>(skippedUsages);

    pResult.addInfo(this.getClass(), unreacheableUsages);
  }

  @Override
  protected void handleFinishSignal(Class<? extends RefinementInterface> callerClass) {
    super.handleFinishSignal(callerClass);
    if (callerClass.equals(IdentifierIterator.class)) {
      //Refinement iteration finishes
      refinedStates.clear();
      usages[0] = null;
      usages[1] = null;
      iterators[0] = null;
      iterators[1] = null;
    } else if (callerClass.equals(PointIterator.class)) {
      skippedUsages.clear();
    }
  }

  @Override
  protected ExtendedARGPath getNextIteration0(int pId) {
    ARGPath currentPath;

    //try to compute more paths
    PathIterator pathIterator = iterators[pId];
    currentPath = pathIterator.nextPath(refinedStates);

    if (currentPath == null) {
      // no path to iterate, finishing
      if (!isFeasiblePath[pId]) {
        skippedUsages.add(usages[pId]);
      }
      return null;
    }
    ExtendedARGPath result = new ExtendedARGPath(currentPath, usages[pId]);
    //Not add result now, only after refinement
    return result;
  }

  private void handleAffectedStates(List<ARGState> affectedStates) {
    if (affectedStates != null) {
      List<Integer> changedStateNumbers = transformedImmutableListCopy(affectedStates, idExtractor);
      refinedStates.add(changedStateNumbers);
    }
  }

  @Override
  protected void resetSecondIterationFor() {
    iterators[1] =
        subgraphComputer.iterator((ARGState) usages[1].getKeyState(), usages[1].getExpandedStack());
  }

  @Override
  protected String getName() {
    return "PathPairIterator";
  }
}
