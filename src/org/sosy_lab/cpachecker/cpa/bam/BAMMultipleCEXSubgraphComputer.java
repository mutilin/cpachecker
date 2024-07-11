// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.bam;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.common.collect.Collections3.transformedImmutableListCopy;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.usage.refinement.PathRestorator;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;

/**
 * The subgraph computer is used to restore paths not to target states, but to any other. The
 * difficulty is to determine the outer block.
 *
 * <p>One more feature of the computer is skipping such paths, which contains special (repeated)
 * states. The feature is extremely important for refinement optimization: we do not refine and even
 * not compute the similar paths
 */
public class BAMMultipleCEXSubgraphComputer extends BAMSubgraphComputer implements PathRestorator {
  private final Function<ARGState, Integer> getStateId;

  private final StatTimer checkRepetitionTimer =
      new StatTimer("Time for checking repetition of path");
  private final StatTimer timeForFindPath = new StatTimer("Time for find path");
  private final StatTimer timeGetOnePathTo = new StatTimer("Time for restore the path in graph");
  private final StatCounter numOfRepeatedPaths = new StatCounter("Number of repeated paths");

  BAMMultipleCEXSubgraphComputer(BAMCPA bamCPA, @NonNull Function<ARGState, Integer> idExtractor) {
    super(bamCPA, true);
    getStateId = idExtractor;
  }

  private ARGState findPath(BackwardARGState newTreeTarget, List<AbstractState> expandedStack)
      throws InterruptedException, MissingBlockException {

    Map<ARGState, BackwardARGState> elementsMap = new HashMap<>();
    ARGState root = null;
    boolean inCallstackFunction = false;
    List<AbstractState> localExpandedStack = new ArrayList<>(expandedStack);

    ARGState target = newTreeTarget.getARGState();
    elementsMap.put(target, newTreeTarget);
    ARGState currentState = target;

    final NavigableSet<ARGState> openElements = new TreeSet<>(target.getParents());
    while (!openElements.isEmpty()) {
      currentState = openElements.pollLast();

      if (elementsMap.containsKey(currentState)) {
        continue; // state already done
      }

      BackwardARGState newCurrentElement = new BackwardARGState(currentState);
      elementsMap.put(currentState, newCurrentElement);

      final Set<BackwardARGState> childrenInSubgraph = new TreeSet<>();
      for (final ARGState child : currentState.getChildren()) {
        // if a child is not in the subgraph, it does not lead to the target, so ignore it.
        // Because of the ordering, all important children should be finished already.
        if (elementsMap.containsKey(child)) {
          childrenInSubgraph.add(elementsMap.get(child));
        }
      }

      if (childrenInSubgraph.isEmpty()) {
        continue;
      }

      inCallstackFunction = false;
      if (currentState.getParents().isEmpty()) {
        // Find correct expanded state
        Collection<AbstractState> expandedStates =
            new TreeSet<>(data.getNonReducedInitialStates(currentState));

        // If we have an expanded stack, use the hint
        if (!localExpandedStack.isEmpty()) {
          AbstractState rightExpanded = localExpandedStack.get(localExpandedStack.size() - 1);
          if (expandedStates.contains(rightExpanded)) {
            expandedStates = ImmutableSet.of(rightExpanded);
            localExpandedStack.remove(localExpandedStack.size() - 1);
          }
        }

        if (expandedStates.isEmpty()) {
          // children are a normal successors -> create an connection from parent to children
          for (final BackwardARGState newChild : childrenInSubgraph) {
            newChild.addParent(newCurrentElement);
          }

          // The first state
          root = newCurrentElement;
          break;
        }

        // Try to find path.
        // Exchange the reduced state by the expanded one
        currentState = (ARGState) expandedStates.iterator().next();
        newCurrentElement = new BackwardARGState(currentState);
        elementsMap.put(currentState, newCurrentElement);
        inCallstackFunction = true;
      }

      // add parent for further processing
      openElements.addAll(currentState.getParents());

      if (data.hasInitialState(currentState) && !inCallstackFunction) {
        // If child-state is an expanded state, the child is at the exit-location of a block.
        // In this case, we enter the block (backwards).
        // We must use a cached reachedSet to process further, because the block has its own
        // reachedSet.
        // The returned 'innerTreeRoot' is the rootNode of the subtree, created from the cached
        // reachedSet.
        // The current subtree (successors of child) is appended beyond the innerTree, to get a
        // complete subgraph.
        computeCounterexampleSubgraphForBlock(newCurrentElement, childrenInSubgraph);
        assert childrenInSubgraph.size() == 1;
        BackwardARGState tmpState = childrenInSubgraph.iterator().next();
        // Check repetition of constructed states
        Deque<ARGState> waitlist = new ArrayDeque<>();
        waitlist.add(tmpState);
        while (!waitlist.isEmpty()) {
          tmpState = (BackwardARGState) waitlist.pop();
          if (tmpState.equals(newCurrentElement)) {
            break;
          }
          waitlist.addAll(tmpState.getParents());
        }

      } else {
        // children are normal successors -> create an connection from parent to children
        for (final BackwardARGState newChild : childrenInSubgraph) {
          newChild.addParent(newCurrentElement);
        }

        if (currentState.getParents().isEmpty()) {
          // The first state
          root = newCurrentElement;
          break;
        }
      }
      if (currentState.isDestroyed()) {
        return null;
      }
    }
    assert root != null;
    return root;
  }

  ARGPath restorePathFrom(
      BackwardARGState pLastElement,
      Set<List<Integer>> pRefinedStates,
      List<AbstractState> pStack) {
    // Note pLastElement may not be the last indeed
    // The path may be recomputed from the middle
    assert (pLastElement != null && !pLastElement.isDestroyed());

    try {
      timeForFindPath.start();
      ARGState rootOfSubgraph = findPath(pLastElement, pStack);
      timeForFindPath.stop();
      assert (rootOfSubgraph != null);
      timeGetOnePathTo.start();
      ARGPath result = ARGUtils.getRandomPath(rootOfSubgraph);
      timeGetOnePathTo.stop();
      if (result != null && checkThePathHasRepeatedStates(result, pRefinedStates)) {
        numOfRepeatedPaths.inc();
        return null;
      }
      return result;
    } catch (MissingBlockException | InterruptedException e) {
      return null;
    }
  }

  @Override
  public ARGPath computePath(ARGState pLastElement, List<AbstractState> pStack) {
    return computePath(pLastElement, ImmutableSet.of(), pStack);
  }

  @Override
  public ARGPath computePath(
      ARGState pLastElement, Set<List<Integer>> pRefinedStates, List<AbstractState> pStack) {
    return restorePathFrom(new BackwardARGState(pLastElement), pRefinedStates, pStack);
  }

  boolean checkThePathHasRepeatedStates(ARGPath path, Set<List<Integer>> pRefinedStates) {
    checkRepetitionTimer.start();
    try {
      List<Integer> ids = transformedImmutableListCopy(path.asStatesList(), getStateId);

      return from(pRefinedStates).anyMatch(ids::containsAll);
    } finally {
      checkRepetitionTimer.stop();
    }
  }

  @Override
  public void printStatistics(StatisticsWriter pOut) {
    pOut.put(checkRepetitionTimer)
        .put(timeForFindPath)
        .put(timeGetOnePathTo)
        .put(numOfRepeatedPaths);
  }

  /**
   * This states is used for UsageStatisticsRefinement: If after some refinement iterations the path
   * goes through already processed states, this marked state is returned.
   */
  public static final BackwardARGState DUMMY_STATE_FOR_REPEATED_STATE =
      new BackwardARGState(new ARGState(null, null));

  @Override
  public BAMSubgraphIterator iterator(ARGState target, List<AbstractState> pStack) {
    return new BAMSubgraphIterator(target, this, data, pStack);
  }
}
