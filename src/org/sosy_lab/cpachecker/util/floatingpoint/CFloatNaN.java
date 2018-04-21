/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.floatingpoint;

import static com.google.common.primitives.Ints.max;

public class CFloatNaN implements CFloat {

  private boolean negative;
  private final int type;

  public CFloatNaN() {
    this(false, CFloatNativeAPI.FP_TYPE_SINGLE);
  }

  public CFloatNaN(int pType) {
    this(false, pType);
  }

  public CFloatNaN(boolean pNegative, int pType) {
    this.negative = pNegative;
    this.type = pType;
  }

  @Override
  public CFloat add(CFloat pSummand) {
    int maxType = max(type, pSummand.getType());
    return new CFloatNaN(negative, maxType);
  }

  @Override
  public CFloat add(CFloat... pSummands) {
    int maxType = type;
    for (CFloat summand : pSummands) {
      maxType = max(maxType, summand.getType());
    }

    return new CFloatNaN(negative, maxType);
  }

  @Override
  public CFloat multiply(CFloat pFactor) {
    int maxType = max(type, pFactor.getType());
    return new CFloatNaN(negative, maxType);
  }

  @Override
  public CFloat multiply(CFloat... pFactors) {
    int maxType = type;
    int sign = negative ? -1 : 1;

    for (CFloat factor : pFactors) {
      maxType = max(maxType, factor.getType());
      sign *= factor.isNegative() ? -1 : 1;
    }

    return new CFloatNaN(sign < 0, maxType);
  }

  @Override
  public CFloat subtract(CFloat pSubtrahend) {
    int maxType = max(type, pSubtrahend.getType());
    return new CFloatNaN(negative, maxType);
  }

  @Override
  public CFloat divideBy(CFloat pDivisor) {
    int maxType = max(type, pDivisor.getType());
    int sign = (negative ? -1 : 1) * (pDivisor.isNegative() ? -1 : 1);
    return new CFloatNaN(sign < 0, maxType);
  }

  @Override
  public CFloat powTo(CFloat pExponent) {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat powToIntegral(int pExponent) {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat sqrt() {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat round() {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat trunc() {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat ceil() {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat floor() {
    return new CFloatNaN(negative, type);
  }

  @Override
  public CFloat abs() {
    return new CFloatNaN(false, type);
  }

  @Override
  public boolean isZero() {
    return false;
  }

  @Override
  public boolean isOne() {
    return false;
  }

  @Override
  public boolean isNegative() {
    return negative;
  }

  @Override
  public CFloat copySignFrom(CFloat pSource) {
    return new CFloatNaN(pSource.isNegative(), type);
  }

  @Override
  public CFloat castTo(int pToType) {
    return new CFloatNaN(negative, pToType);
  }

  @Override
  public Number castToOther(int pToType) {
    // TODO Determine behavior for other types than floating point
    return null;
  }

  @Override
  public CFloatWrapper copyWrapper() {
    // TODO is there a wrapper necessary at all?
    return null;
  }

  @Override
  public int getType() {
    return type;
  }

  @Override
  public boolean isNan() {
    return true;
  }

  @Override
  public String toString() {
    return (negative ? "-" : "") + "nan";
  }
}
