// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.signal;

import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;

public class EmptyVisitor implements CFAVisitor {

  @Override
  public SignalEffect visitDeclarationEdge(CDeclarationEdge pDeclEdge) {
    return EmptySignalEffect.getInstance();
  }

  @Override
  public SignalEffect visitFunctionCallEdge(CFunctionCallEdge pFcallEdge) {
    return EmptySignalEffect.getInstance();
  }

  @Override
  public SignalEffect visitStatementEdge(CStatementEdge pStmntEdge) {
    return EmptySignalEffect.getInstance();
  }

}
