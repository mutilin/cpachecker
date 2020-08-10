/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.pointer2;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.pointer2.util.ExplicitLocationSet;
import org.sosy_lab.cpachecker.cpa.pointer2.util.LocationSet;
import org.sosy_lab.cpachecker.cpa.pointer2.util.LocationSetBot;
import org.sosy_lab.cpachecker.cpa.pointer2.util.LocationSetTop;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/**
 * Instances of this class are pointer states that are used as abstract elements
 * in the pointer CPA.
 */
public class PointerState implements AbstractState {

  /**
   * The initial empty pointer state.
   */
  public static final PointerState INITIAL_STATE = new PointerState();

  /**
   * The points-to map of the state.
   */
  private PersistentSortedMap<MemoryLocation, ExplicitLocationSet> pointsToMap;

  private SortedSet<MemoryLocation> topLocations;

  /**
   * Creates a new pointer state with an empty initial points-to map.
    */
  private PointerState() {
    pointsToMap = PathCopyingPersistentTreeMap.of();
    topLocations = new TreeSet<>();
  }

  /**
   * Creates a new pointer state from the given persistent points-to map.
   *
   * @param pPointsToMap the points-to map of this state.
   */
  private PointerState(
      PersistentSortedMap<MemoryLocation, ExplicitLocationSet> pPointsToMap,
      SortedSet<MemoryLocation> pTop) {
    this.pointsToMap = pPointsToMap;
    this.topLocations = pTop;
  }

  /**
   * Gets a pointer state representing the points to information of this state
   * combined with the information that the first given identifier points to the
   * second given identifier.
   *
   * @param pSource the first identifier.
   * @param pTarget the second identifier.
   * @return the pointer state.
   */
  public PointerState addPointsToInformation(MemoryLocation pSource, MemoryLocation pTarget) {
    LocationSet previousPointsToSet = getPointsToSet(pSource);
    if (previousPointsToSet == LocationSetTop.INSTANCE) {
      return this;
    }
    LocationSet newPointsToSet = previousPointsToSet.addElement(pTarget);
    assert newPointsToSet instanceof ExplicitLocationSet;
    return new PointerState(
        pointsToMap.putAndCopy(pSource, (ExplicitLocationSet) newPointsToSet),
        topLocations);
  }

  /**
   * Gets a pointer state representing the points to information of this state
   * combined with the information that the first given identifier points to the
   * given target identifiers.
   *
   * @param pSource the first identifier.
   * @param pTargets the target identifiers.
   * @return the pointer state.
   */
  public PointerState addPointsToInformation(MemoryLocation pSource, Iterable<MemoryLocation> pTargets) {
    LocationSet previousPointsToSet = getPointsToSet(pSource);
    if (previousPointsToSet == LocationSetTop.INSTANCE) {
      return this;
    }
    LocationSet newPointsToSet = previousPointsToSet.addElements(pTargets);
    assert newPointsToSet instanceof ExplicitLocationSet;
    return new PointerState(
        pointsToMap.putAndCopy(pSource, (ExplicitLocationSet) newPointsToSet),
        topLocations);
  }

  /**
   * Gets a pointer state representing the points to information of this state
   * combined with the information that the first given identifier points to the
   * given target identifiers.
   *
   * @param pSource the first identifier.
   * @param pTargets the target identifiers.
   * @return the pointer state.
   */
  public PointerState addPointsToInformation(MemoryLocation pSource, LocationSet pTargets) {
    if (pTargets.isBot()) {
      return this;
    }
    if (pTargets.isTop()) {
      return addAsTop(pSource);
    }
    LocationSet previousPointsToSet = getPointsToSet(pSource);
    LocationSet newSet = previousPointsToSet.addElements(pTargets);
    if (newSet == previousPointsToSet) {
      // Including top
      return this;
    }
    assert newSet instanceof ExplicitLocationSet;
    return new PointerState(
        pointsToMap.putAndCopy(pSource, (ExplicitLocationSet) newSet),
        topLocations);
  }

  public PointerState addAsTop(MemoryLocation pSource) {
    if (topLocations.contains(pSource)) {
      return this;
    }
    SortedSet<MemoryLocation> newSet = new TreeSet<>(topLocations);
    if (newSet.add(pSource)) {
      return new PointerState(pointsToMap.removeAndCopy(pSource), newSet);
    } else {
      // Need to return the same object
      return new PointerState(pointsToMap.removeAndCopy(pSource), topLocations);
    }
  }

  public PointerState addAsTop(Set<MemoryLocation> pSources) {
    if (topLocations == pSources || pSources.isEmpty()) {
      return this;
    }
    SortedSet<MemoryLocation> newSet = new TreeSet<>(topLocations);
    PersistentSortedMap<MemoryLocation, ExplicitLocationSet> result = pointsToMap;
    boolean modified = false;

    for (MemoryLocation loc : pSources) {
      if (newSet.add(loc)) {
        modified = true;
        result = pointsToMap.removeAndCopy(loc);
      }
    }
    if (!modified) {
      // Do not check result==pointsToMap!
      return this;
    } else {
      return new PointerState(result, newSet);
    }
  }

  /**
   * Gets the points-to set mapped to the given identifier.
   *
   * @param pSource the identifier pointing to the points-to set in question.
   * @return the points-to set of the given identifier.
   */
  public LocationSet getPointsToSet(MemoryLocation pSource) {
    if (topLocations.contains(pSource)) {
      return LocationSetTop.INSTANCE;
    }
    LocationSet result = this.pointsToMap.get(pSource);
    if (result == null) {
      return LocationSetBot.INSTANCE;
    }
    return result;
  }

  /**
   * Checks whether or not the first identifier points to the second identifier.
   *
   * @param pSource the first identifier.
   * @param pTarget the second identifier.
   * @return <code>true</code> if the first identifier definitely points to the
   * second identifier, <code>false</code> if it definitely does not point to
   * the second identifier and <code>null</code> if it might point to it.
   */
  @Nullable
  public Boolean pointsTo(MemoryLocation pSource, MemoryLocation pTarget) {
    LocationSet pointsToSet = getPointsToSet(pSource);
    if (pointsToSet.equals(LocationSetBot.INSTANCE)) {
      return false;
    }
    if (pointsToSet instanceof ExplicitLocationSet) {
      ExplicitLocationSet explicitLocationSet = (ExplicitLocationSet) pointsToSet;
      if (explicitLocationSet.mayPointTo(pTarget)) {
        return explicitLocationSet.getSize() == 1 ? true : null;
      } else {
        return false;
      }
    }
    return null;
  }

  /**
   * Checks whether or not the first identifier is known to point to the second
   * identifier.
   *
   * @return <code>true</code> if the first identifier definitely points to the
   * second identifier, <code>false</code> if it might point to it or is known
   * not to point to it.
   */
  public boolean definitelyPointsTo(MemoryLocation pSource, MemoryLocation pTarget) {
    return Boolean.TRUE.equals(pointsTo(pSource, pTarget));
  }

  /**
   * Checks whether or not the first identifier is known to not point to the
   * second identifier.
   *
   * @return <code>true</code> if the first identifier definitely does not
   * points to the second identifier, <code>false</code> if it might point to
   * it or is known to point to it.
   */
  public boolean definitelyNotPointsTo(MemoryLocation pSource, MemoryLocation pTarget) {
    return Boolean.FALSE.equals(pointsTo(pSource, pTarget));
  }

  /**
   * Checks whether or not the first identifier is may point to the second
   * identifier.
   *
   * @return <code>true</code> if the first identifier definitely points to the
   * second identifier or might point to it, <code>false</code> if it is known
   * not to point to it.
   */
  public boolean mayPointTo(MemoryLocation pSource, MemoryLocation pTarget) {
    return !Boolean.FALSE.equals(pointsTo(pSource, pTarget));
  }

  /**
   * Gets all locations known to the state.
   *
   * @return all locations known to the state.
   */
  public Set<MemoryLocation> getKnownLocations() {
    return ImmutableSet.copyOf(
        Iterables.concat(
            topLocations,
            pointsToMap.keySet(),
            FluentIterable.from(pointsToMap.values())
                .transformAndConcat(
                    new Function<LocationSet, Iterable<? extends MemoryLocation>>() {

      @Override
      public Iterable<? extends MemoryLocation> apply(LocationSet pArg0) {
        if (pArg0 instanceof ExplicitLocationSet) {
          return (ExplicitLocationSet) pArg0;
        }
        return ImmutableSet.of();
      }

    })));
  }

  /**
   * Gets the points-to map of this state.
   *
   * @return the points-to map of this state.
   */
  public Map<MemoryLocation, LocationSet> getPointsToMap() {
    return Collections.unmodifiableMap(this.pointsToMap);
  }

  public Map<MemoryLocation, LocationSet> prepareOriginMap() {
    Map<MemoryLocation, LocationSet> newMap = new TreeMap<>(pointsToMap);
    for (MemoryLocation loc : topLocations) {
      newMap.put(loc, LocationSetTop.INSTANCE);
    }
    return Collections.unmodifiableMap(newMap);
  }

  public Set<MemoryLocation> getTopSet() {
    // Avoid immutable to be able to check on equality
    return this.topLocations;
  }

  @Override
  public boolean equals(Object pO) {
    if (this == pO) {
      return true;
    }
    if (pO instanceof PointerState) {
      PointerState other = ((PointerState) pO);
      if (topLocations.size() != other.topLocations.size() || pointsToMap.size() != other.pointsToMap.size()) {
        return false;
      }
      boolean a = topLocations == other.topLocations;
      boolean b = pointsToMap == other.pointsToMap;

      if (a && b) {
        return true;
      }
      if (!a) {
        a = topLocations.equals(other.topLocations);
        if (a) {
          topLocations = other.topLocations;
        }
      }
      if (a && !b) {
        b = pointsToMap.equals(other.pointsToMap);
        if (b) {
          pointsToMap = other.pointsToMap;
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pointsToMap, topLocations);
  }

  @Override
  public String toString() {
    return pointsToMap.toString() + ",\n Top: " + topLocations;
  }

  public static PointerState copyOf(PointerState pState) {
    return new PointerState(
        PathCopyingPersistentTreeMap.copyOf(pState.pointsToMap),
        new TreeSet<>(pState.topLocations));
  }

  public PointerState forget(MemoryLocation pPtr) {
    SortedSet<MemoryLocation> newSet = topLocations;
    if (topLocations.contains(pPtr)) {
      newSet = new TreeSet<>(topLocations);
      newSet.remove(pPtr);
    }
    return new PointerState(pointsToMap.removeAndCopy(pPtr), newSet);
  }

  public Set<MemoryLocation> getTrackedMemoryLocations() {
    return Sets.union(pointsToMap.keySet(), topLocations);
  }

  public static boolean isFictionalPointer(MemoryLocation ptr) {
    return ptr.getIdentifier().contains("##");
  }

  public int getSize() {
    return pointsToMap.size() + topLocations.size();
  }
}
