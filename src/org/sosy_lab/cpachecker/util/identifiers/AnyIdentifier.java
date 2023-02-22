// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.identifiers;

import com.google.common.collect.ImmutableList;
import java.util.Collection;

public class AnyIdentifier implements AbstractIdentifier {

  private final static AnyIdentifier instance = new AnyIdentifier();

  @Override
  public int compareTo(AbstractIdentifier pArg0) {
    if (pArg0 == instance) {
      return 0;
    }
    return -1;
  }

  @Override
  public boolean isGlobal() {
    return true;
  }

  @Override
  public AbstractIdentifier cloneWithDereference(int pDereference) {
    return instance;
  }

  @Override
  public boolean equals(Object pOther) {
    if (pOther == instance) {
      return true;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public int getDereference() {
    // Just in case
    return 1;
  }

  @Override
  public boolean isPointer() {
    return true;
  }

  @Override
  public boolean isDereferenced() {
    return true;
  }

  @Override
  public String toString() {
    return "Unknown";
  }

  @Override
  public Collection<AbstractIdentifier> getComposedIdentifiers() {
    return ImmutableList.of();
  }

  public static AnyIdentifier getInstance() {
    return instance;
  }
}
