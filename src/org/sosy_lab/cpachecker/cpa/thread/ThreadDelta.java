// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.thread;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState.ThreadStatus;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.storage.Delta;
import org.sosy_lab.cpachecker.cpa.usage.storage.GenericDelta;

public class ThreadDelta implements Delta<CompatibleNode> {

  protected final Map<String, ThreadStatus> threadSet;
  // Do not support rSet now

  ThreadDelta(Map<String, ThreadStatus> tSet) {
    threadSet = tSet;
  }

  @Override
  public CompatibleNode apply(CompatibleNode pState) {
    if (threadSet.isEmpty()) {
      return pState;
    }
    ThreadState pOther = (ThreadState) pState;
    Map<String, ThreadStatus> reduced = pOther.getThreadSet();
    if (reduced.isEmpty()) {
      return pOther.copyWith(threadSet);
    }

    Map<String, ThreadStatus> newSet = mergeMaps(pOther.threadSet);
    return pOther.copyWith(newSet);
  }

  @Override
  public boolean covers(Delta<CompatibleNode> pDelta) {
    if (pDelta instanceof ThreadDelta) {
      ThreadDelta pOther = (ThreadDelta) pDelta;
      // TODO contains?
      return threadSet.entrySet().containsAll(pOther.threadSet.entrySet());
    } else if (pDelta == GenericDelta.getInstance()) {
      return true;
    } else {
      throw new UnsupportedOperationException("Unknown delta " + pDelta.getClass());
    }
  }

  @Override
  public Delta<CompatibleNode> add(Delta<CompatibleNode> pDelta) {
    if (pDelta instanceof ThreadDelta) {
      ThreadDelta pOther = (ThreadDelta) pDelta;
      if (pOther.threadSet.isEmpty()) {
        return this;
      }
      if (threadSet.isEmpty()) {
        return pDelta;
      }
      Map<String, ThreadStatus> newSet = mergeMaps(pOther.threadSet);
      return copy(newSet);
    } else if (pDelta == GenericDelta.getInstance()) {
      return this;
    } else {
      throw new UnsupportedOperationException("Unknown delta " + pDelta.getClass());
    }
  }

  private Map<String, ThreadStatus> mergeMaps(Map<String, ThreadStatus> other) {
    Map<String, ThreadStatus> newSet = new TreeMap<>(threadSet);
    for (Entry<String, ThreadStatus> entry : other.entrySet()) {
      if (newSet.containsKey(entry.getKey())) {
        addTheSameStatus(newSet, entry.getKey());
      } else {
        newSet.put(entry.getKey(), entry.getValue());
      }
    }
    return newSet;
  }

  protected void
      addTheSameStatus(@SuppressWarnings("unused") Map<String, ThreadStatus> pNewSet, String pKey) {
    // Without options throw exception
    throw new UnsupportedOperationException(pKey + " already exists");
  }

  @Override
  public int hashCode() {
    return Objects.hash(threadSet);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof ThreadDelta)) {
      return false;
    }
    ThreadDelta other = (ThreadDelta) obj;
    return Objects.equals(threadSet, other.threadSet);
  }

  public ThreadDelta copy(Map<String, ThreadStatus> pTSet) {
    return new ThreadDelta(pTSet);
  }

  public static class SelfParallelDelta extends ThreadDelta {

    SelfParallelDelta(Map<String, ThreadStatus> pTSet) {
      super(pTSet);
    }

    @Override
    protected void addTheSameStatus(Map<String, ThreadStatus> pNewSet, String pKey) {
      pNewSet.put(pKey, ThreadStatus.SELF_PARALLEL_THREAD);
    }

    @Override
    public SelfParallelDelta copy(Map<String, ThreadStatus> pTSet) {
      return new SelfParallelDelta(pTSet);
    }
  }

  public static class SkipThreadDelta extends ThreadDelta {

    SkipThreadDelta(Map<String, ThreadStatus> pTSet) {
      super(pTSet);
    }

    @Override
    protected void addTheSameStatus(Map<String, ThreadStatus> pNewSet, String pKey) {
      // Do nothing - skip
    }

    @Override
    public SkipThreadDelta copy(Map<String, ThreadStatus> pTSet) {
      return new SkipThreadDelta(pTSet);
    }
  }
}
