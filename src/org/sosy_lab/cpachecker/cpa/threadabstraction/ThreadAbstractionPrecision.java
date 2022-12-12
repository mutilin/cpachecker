// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadabstraction;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.Precision;

public class ThreadAbstractionPrecision implements Precision {

  private final ImmutableSet<CFAEdge> prec;

  private ThreadAbstractionPrecision(ImmutableSet<CFAEdge> pPrec) {
    prec = pPrec;
  }

  ThreadAbstractionPrecision() {
    this(ImmutableSet.of());
  }

  public ThreadAbstractionPrecision copyWith(Set<CFAEdge> pEdges) {
    ImmutableSet.Builder<CFAEdge> newPrec = ImmutableSet.builder();
    newPrec.addAll(prec);
    newPrec.addAll(pEdges);
    return new ThreadAbstractionPrecision(newPrec.build());
  }

  public boolean contains(CFAEdge edge) {
    if (prec.isEmpty() || edge == ThreadAbstractionEdge.EMPTY_EDGE) {
      return false;
    }
    return prec.contains(edge);
  }

  public int size() {
    return prec.size();
  }

  @Override
  public String toString() {
    return prec.toString();
  }
}
