/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosy_lab.common.Pair;
import org.sosy_lab.common.Triple;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentLinkedList;
import org.sosy_lab.common.collect.PersistentList;
import org.sosy_lab.common.collect.PersistentSortedMap;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap.SSAMapBuilder;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTarget;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSetBuilder;
import org.sosy_lab.solver.api.Formula;


public class BnBRegionsMaker {

  private List<BnBRegionImpl> regions = new ArrayList<>();
  private static final int GLOBAL = -1;
  private Set<CType> containers = new HashSet<>();
  private Map<String, List<PointerTarget>> targetRegions = new HashMap<>();
  private Map<PointerTarget, Triple<String, String, Integer>> pointerTargets = new HashMap<>();

  public List<BnBRegionImpl> getRegions(){
    return regions;
  }

  /**
   *
   * @param parent - name of element's base
   * @param name - name of element
   * @return index or -1 if global
   */
  public int getRegionIndexByParentAndName(String parent, String name){

    BnBRegionImpl current;

    for (int i = 0; i < regions.size(); ++i){
      current = regions.get(i);
      if (current.getElem().equals(name)
          && current.getRegionParent().toString().equals(parent)){

        if (current.isPartOfGlobal()){
          return GLOBAL;
        } else {
          return i;
        }

      }
    }

    return GLOBAL;
  }

  public Set<CType> getContainers(){
    return containers;
  }

  public void makeRegions(CFA cfa) {
    ComplexTypeFieldStatistics ctfs = new ComplexTypeFieldStatistics();
    ctfs.findFieldsInCFA(cfa);

    Map<CType, HashMap<CType, HashSet<Pair<String, Integer>>>> usedFields = ctfs.getUsedFields();
    Map<CType, HashMap<CType, HashSet<Pair<String, Integer>>>> refdFields = ctfs.getRefdFields();
    Map<CType, HashSet<Pair<String, Integer>>> sub;

    // remove all those fields present in both maps
    for (CType basicType : usedFields.keySet()){
      if (refdFields.containsKey(basicType)){
        sub = usedFields.get(basicType);
        for (CType structType : sub.keySet()){
          HashSet<Pair<String, Integer>> set = refdFields.get(basicType).get(structType);

          if (set != null){
            usedFields.get(basicType).get(structType).removeAll(set);
          }

        }
      }
    }

    // fill regions
    for (CType basicType : refdFields.keySet()){
      for (CType structType : refdFields.get(basicType).keySet()){
        for (Pair<String, Integer> name : refdFields.get(basicType).get(structType)){

          regions.add(new BnBRegionImpl(basicType, null, structType.toString() + "::" + name.getFirst(), name.getSecond()));

        }
      }
    }

    for (CType basicType : usedFields.keySet()){
      for (CType structType : usedFields.get(basicType).keySet()){

        Set<Pair<String, Integer>> set = usedFields.get(basicType).get(structType);
        if (!set.isEmpty()) {
          for (Pair<String, Integer> name : set){
            regions.add(new BnBRegionImpl(basicType, (CCompositeType) structType, name.getFirst(), name.getSecond()));
          }
        }
      }
    }

    for (BnBRegionImpl region : regions){
      containers.add(region.getRegionParent());
    }

  }

  public void dumpRegions(String filename){
    File dump = new File(filename);

    try{
      FileWriter writer = new FileWriter(dump);

      String result = "";
      int i = 0;
      for (BnBRegionImpl reg : regions){
        result += "Number: " + (i++) + '\n';
        result += "Type: " + reg.getType().toString() + '\n';
        result += "Parent: ";

        if (reg.getRegionParent() == null){
          result += "NULL";
        } else {
          result += reg.getRegionParent().toString();
        }
        result += '\n';
        result += "Member:\n";
        result += '\t' + reg.getElem() + "\n\n";

      }

      writer.write(result);
      writer.close();

    } catch (IOException e){
      System.out.println(e.getMessage());
    }
  }

  public int getRegionIndex(CType pType, Formula pAddress, SSAMapBuilder ssa) {
    String addr = pAddress.toString();
    String ssaAddr = addr;
    int offset = 0;

    addr = addr.replaceAll("@", "::");
    addr = addr.replaceAll("[^a-zA-Z:0-9]", "");
    System.out.println(addr);
    for (int i = addr.length() - 1; i > 1; --i){
      if (addr.charAt(i) >= '0' && addr.charAt(i) <= '9' && addr.charAt(i - 1) != ':'){
        offset += addr.charAt(i) - '0';
      } else if (addr.charAt(i - 1) == ':'){
        break;
      }
    }
    addr = addr.split("::")[1];
    System.out.println("NEW_ADDR: " + addr);

    int lastIndex = ssaAddr.indexOf('@');
    int firstIndex = ssaAddr.indexOf('|');
    String newSsaAddr = "";
    for (int i = firstIndex + 1; i < lastIndex; ++i){
      newSsaAddr += ssaAddr.charAt(i);
    }

    CType parentType = ssa.getType(newSsaAddr);
    String str = parentType.toString().replaceAll("[()*]", "");
    System.out.println("Parent: " + str);
    System.out.println(newSsaAddr);
    System.out.println(offset);

    return getRegionIndex(str, offset);

  }

  public Map<String, PersistentList<PointerTarget>> getNewTargetsWithRegions(
      PersistentSortedMap<String, PersistentList<PointerTarget>> targets,
      PointerTargetSetBuilder ptsb){

    for (String target : targets.keySet()){
      for (PointerTarget pt : targets.get(target)){
        CType containerType = pt.getContainerType();
        if (containers.contains(containerType)
            && containerType instanceof CCompositeType){
          int offset = pt.getContainerOffset();
          int curOffset = 0;

          for (CCompositeTypeMemberDeclaration field : ((CCompositeType) containerType).getMembers()){
            if (curOffset == offset){
              pointerTargets.put(pt, Triple.of(target, containerType.toString() + " " + field.getName(),
                  getRegionIndexByParentAndName(containerType.toString(), field.getName())));
            } else {
              offset += ptsb.getSize(field.getType());
            }
          }
        } else {
          pointerTargets.put(pt, Triple.of(target, "global", -1));
        }
      }
    }

    for (PointerTarget pt : pointerTargets.keySet()){
      Triple<String, String, Integer> triple = pointerTargets.get(pt);
      String key;
      if (triple.getFirst().contains("global") || triple.getFirst().contains("struct")){
        key = triple.getFirst();
      } else {
        key = triple.getFirst() + " " + triple.getSecond();
      }
      if (!targetRegions.containsKey(key)){
        targetRegions.put(key, new ArrayList<PointerTarget>());
      }
      if (!targetRegions.get(key).contains(pt)){
        targetRegions.get(key).add(pt);
      }
    }

    for (PointerTarget pt : pointerTargets.keySet()){
      System.out.println("UTR: " + pt.getBase() + ' ' +
          pt.getOffset() + ' ' + targetRegions.get(pt));
    }

    System.out.println(targetRegions);
    System.out.println(targets);
    System.out.println("UTR: ###########");

    Map<String, PersistentList<PointerTarget>> newTargets = new HashMap<>();
    for (String type : targetRegions.keySet()){
      PersistentList<PointerTarget> pll =
          PersistentLinkedList.copyOf(targetRegions.get(type));
      newTargets.put(type, pll);
    }

    for (String type : targets.keySet()){
      if (! (type.contains("global") || type.contains("struct"))){
        List<PointerTarget> toAdd = new ArrayList<>();
        for (PointerTarget pt : targets.get(type)){
          if (getRegionsIndex(pt) < 0){
            toAdd.add(pt);
          }
        }
        newTargets.put(type + " global", PersistentLinkedList.copyOf(toAdd));
      } else {
        newTargets.put(type, targets.get(type));
      }
    }

    return newTargets;
  }

  public int getRegionsIndex(PointerTarget pt){
    if (pointerTargets.keySet().contains(pt)){
      return pointerTargets.get(pt).getThird();
    } else {
      return GLOBAL;
    }
  }

  public int getRegionIndex(String parentType, int offset) {
    for (PointerTarget pt : pointerTargets.keySet()){
      Triple<String, String, Integer> triple = pointerTargets.get(pt);
      System.out.println("PT: " + pt);
      System.out.println("PO: " + pt.getProperOffset());
      System.out.println("Triple: " + triple);
      if (pt.getContainerType().toString().equals(parentType) && pt.getProperOffset() == offset){
        System.out.println("FOUND " + triple.getThird());
        return triple.getThird();
      }
    }
    System.out.println("WILL BE GLOBAL");
    return GLOBAL;
  }

  public BnBRegionImpl getRegion(int pIndex) {
    return regions.get(pIndex);
  }

  public PointerTargetSet updatePTS(PointerTargetSet pts) {
    return new PointerTargetSet(pts, getPersistentTargets());
  }

  private PersistentSortedMap<String, PersistentList<PointerTarget>> getPersistentTargets() {
    Map<String, PersistentList<PointerTarget>> result = new HashMap<>();
    for (String key: targetRegions.keySet()){
      PersistentList<PointerTarget> pl = PersistentLinkedList.copyOf(targetRegions.get(key));
      result.put(key, pl);
    }
    return PathCopyingPersistentTreeMap.copyOf(result);
  }

}
