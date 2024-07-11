// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.local;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.core.interfaces.AdjustablePrecision;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;

public class LocalPrecision implements AdjustablePrecision {

  private final Set<AbstractIdentifier> ids;

  public LocalPrecision(Set<AbstractIdentifier> pIds) {
    ids = pIds;
  }

  @Override
  public AdjustablePrecision add(AdjustablePrecision pOtherPrecision) {
    if (pOtherPrecision.isEmpty()) {
      return this;
    }
    LocalPrecision pOther = (LocalPrecision) pOtherPrecision;
    Set<AbstractIdentifier> newIds = new TreeSet<>(ids);
    newIds.addAll(pOther.ids);
    if (newIds.equals(ids)) {
      return this;
    }
    return new LocalPrecision(newIds);
  }

  @Override
  public AdjustablePrecision subtract(AdjustablePrecision pOtherPrecision) {
    if (pOtherPrecision.isEmpty()) {
      return this;
    }
    LocalPrecision pOther = (LocalPrecision) pOtherPrecision;
    Set<AbstractIdentifier> newIds = new TreeSet<>(ids);
    newIds.removeAll(pOther.ids);
    if (newIds.equals(ids)) {
      return this;
    }
    return new LocalPrecision(newIds);
  }

  @Override
  public boolean isEmpty() {
    return ids.isEmpty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(ids);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    return obj instanceof LocalPrecision other && ids.equals(other.ids);
  }

  Set<AbstractIdentifier> getIds() {
    return ids;
  }

  @Override
  public String toString() {
    return ids.toString();
  }
}
