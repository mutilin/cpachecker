// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage;

import static org.sosy_lab.common.collect.Collections3.transformedImmutableListCopy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.lock.AbstractLockState;
import org.sosy_lab.cpachecker.cpa.lock.LockState;
import org.sosy_lab.cpachecker.cpa.usage.storage.UsagePoint;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;

public final class UsageInfo implements Comparable<UsageInfo> {

  public static enum Access {
    WRITE,
    READ;
  }

  private static class UsageCore {
    private final CFANode node;
    private final Access accessType;
    private AbstractState keyState;
    private List<CFAEdge> path;
    private final SingleIdentifier id;

    private boolean isLooped;

    private UsageCore(@Nonnull Access atype, @Nonnull CFANode n, SingleIdentifier ident) {
      node = n;
      accessType = atype;
      keyState = null;
      isLooped = false;
      id = ident;
    }
  }

  private static final UsageInfo IRRELEVANT_USAGE = new UsageInfo();

  private final UsageCore core;
  private final ImmutableList<CompatibleState> compatibleStates;

  private UsageInfo() {
    core = null;
    compatibleStates = null;
  }

  private UsageInfo(
      @Nonnull Access atype,
      @Nonnull CFANode n,
      SingleIdentifier ident,
      ImmutableList<CompatibleState> pStates) {
    this(new UsageCore(atype, n, ident), pStates);
  }

  private UsageInfo(UsageCore pCore, ImmutableList<CompatibleState> pStates) {
    core = pCore;
    compatibleStates = pStates;
  }

  public static UsageInfo createUsageInfo(
      @NonNull Access atype, @NonNull AbstractState state, AbstractIdentifier ident) {
    if (ident instanceof SingleIdentifier) {
      ImmutableList.Builder<CompatibleState> storedStates = ImmutableList.builder();

      for (AbstractState s : AbstractStates.asIterable(state)) {
        if (s instanceof CompatibleState) {
          if (!((CompatibleState) s).isRelevantFor((SingleIdentifier) ident)) {
            return IRRELEVANT_USAGE;
          }
          storedStates.add(((CompatibleState) s).prepareToStore());
        }
      }
      UsageInfo result =
          new UsageInfo(
              atype,
              AbstractStates.extractLocation(state),
              (SingleIdentifier) ident,
              storedStates.build());
      result.core.keyState = state;
      return result;
    }
    return IRRELEVANT_USAGE;
  }

  public @Nonnull CFANode getCFANode() {
    return core.node;
  }

  public @NonNull SingleIdentifier getId() {
    assert (core.id != null);
    return core.id;
  }

  public void setAsLooped() {
    core.isLooped = true;
  }

  public boolean isLooped() {
    return core.isLooped;
  }

  public boolean isRelevant() {
    return this != IRRELEVANT_USAGE;
  }

  @Override
  public int hashCode() {
    return Objects.hash(core.accessType, core.node, compatibleStates);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    UsageInfo other = (UsageInfo) obj;
    return core.accessType == other.core.accessType
        && Objects.equals(core.node, other.core.node)
        && Objects.equals(compatibleStates, other.compatibleStates);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append(core.accessType);
    sb.append(" access to ");
    sb.append(core.id);
    AbstractLockState locks = getLockState();
    if (locks == null) {
      // Lock analysis is disabled
    } else if (locks.getSize() == 0) {
      sb.append(" without locks");
    } else {
      sb.append(" with ");
      sb.append(locks);
    }

    sb.append(", ");
    sb.append(core.node);

    return sb.toString();
  }

  public void setRefinedPath(List<CFAEdge> p) {
    core.keyState = null;
    core.path = p;
  }

  public AbstractState getKeyState() {
    return core.keyState;
  }

  public List<CFAEdge> getPath() {
    // assert path != null;
    return core.path;
  }

  @Override
  public int compareTo(UsageInfo pO) {
    int result;

    if (this == pO) {
      return 0;
    }
    Preconditions.checkArgument(
        compatibleStates.size() == pO.compatibleStates.size(),
        "Different compatible states in usages are not supported");
    Iterator<CompatibleState> iterator = compatibleStates.iterator();
    Iterator<CompatibleState> otherIterator = pO.compatibleStates.iterator();

    while (iterator.hasNext()) {
      CompatibleState currentState = iterator.next();
      CompatibleState otherState = otherIterator.next();
      Preconditions.checkArgument(
          currentState.getClass() == otherState.getClass(),
          "Different compatible states in usages are not supported");
      // Revert order to negate the result:
      // Usages without locks are more convenient to analyze
      result = otherState.compareTo(currentState);
      if (result != 0) {
        return result;
      }
    }

    result = this.core.node.compareTo(pO.core.node);
    if (result != 0) {
      return result;
    }
    result = this.core.accessType.compareTo(pO.core.accessType);
    if (result != 0) {
      return result;
    }
    /* We can't use key states for ordering, because the treeSets can't understand,
     * that old refined usage with zero key state is the same as new one
     */
    if (this.core.id != null && pO.core.id != null) {
      // Identifiers may not be equal here:
      // if (a.b > c.b)
      // FieldIdentifiers are the same (when we add to container),
      // but full identifiers (here) are not equal
      // TODO should we distinguish them?

    }
    return 0;
  }

  public UsageInfo copy() {
    return copy(compatibleStates);
  }

  private UsageInfo copy(ImmutableList<CompatibleState> newStates) {
    return new UsageInfo(core, newStates);
  }

  public UsageInfo expand(LockState expandedState) {
    ImmutableList.Builder<CompatibleState> builder = ImmutableList.builder();

    for (CompatibleState state : this.compatibleStates) {
      if (state instanceof AbstractLockState) {
        builder.add(expandedState);
      } else {
        builder.add(state);
      }
    }
    return copy(builder.build());
  }

  public AbstractLockState getLockState() {
    for (CompatibleState state : compatibleStates) {
      if (state instanceof AbstractLockState) {
        return (AbstractLockState) state;
      }
    }
    return null;
  }

  public UsagePoint createUsagePoint() {
    List<CompatibleNode> nodes = getCompatibleNodes();

    return new UsagePoint(nodes, core.accessType);
  }

  List<CompatibleNode> getCompatibleNodes() {
    return transformedImmutableListCopy(compatibleStates, CompatibleState::getCompatibleNode);
  }
}
