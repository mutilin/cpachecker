// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2023 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.waitlist;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.lock.AbstractLockState;
import org.sosy_lab.cpachecker.util.AbstractStates;

public class LockSortedWaitlist extends AbstractSortedWaitlist<Integer> {

  protected LockSortedWaitlist(WaitlistFactory pSecondaryStrategy) {
    super(pSecondaryStrategy);
  }

  @Override
  protected Integer getSortKey(AbstractState pState) {
    AbstractLockState lockState =
        AbstractStates.extractStateByType(pState, AbstractLockState.class);
    assert lockState != null : "Disable LockSortedWaitlist without LockCPA";
    // Largest K sorted first
    // We need to consider empty lock sets first
    return -lockState.getSize();
  }

  public static WaitlistFactory factory(final WaitlistFactory pSecondaryStrategy) {
    return () -> new LockSortedWaitlist(pSecondaryStrategy);
  }
}
