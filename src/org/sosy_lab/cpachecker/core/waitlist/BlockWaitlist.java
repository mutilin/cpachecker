/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.waitlist;

import com.google.common.base.Preconditions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.util.AbstractStates;


public class BlockWaitlist implements Waitlist {

  private static CallstackState retreiveCallstack(AbstractState pState) {
    return AbstractStates.extractStateByType(pState, CallstackState.class);
  }

  private static class Block {
    //function name which is the basis for the block
    private String name;
    //current number of used resources
    private int countResources;
    //saved number of resources when limit is reached
    private int savedResources;
    //limit for resources
    private int limitResources;
    //main waitlist
    private Waitlist mainWaitlist;
    //additional waitlist
    private Waitlist extraWaitlist;
    //last state from which the function going into the next block is called
    private CallstackState lastCallstack;
    //is it a block for entry function
    private boolean isEntryBlock;
    //previous block in the list
    private Block prev;

    Block(WaitlistFactory factory) {
      mainWaitlist = factory.createWaitlistInstance();
      extraWaitlist = factory.createWaitlistInstance();
    }

    @SuppressWarnings("unused")
    public int getSavedResources() {
      return savedResources;
    }

    CallstackState getLastCallstack() {
      return lastCallstack;
    }

    /**
     * Add state to main waitlist,
     * increment used resources
     */
    void addStateToMain(AbstractState e) {
      mainWaitlist.add(e);
      incResources(e);
      lastCallstack = retreiveCallstack(e);
    }

    /**
     * Add state to extra waitlist,
     * increment used resources
     * @param e the state to be added
     */
    void addStateToExtra(AbstractState e) {
      extraWaitlist.add(e);
      incResources(e);
    }

    /**
     * check resource limits
     * @return true if resource limit has been reached
     */
    boolean checkResources() {
      if(isEntryBlock) {
        //ignore
        return false;
      } else {
        return countResources > limitResources;
      }
    }

    @SuppressWarnings("unused")
    private void incResources(AbstractState e) {
      countResources++;
    }

    @SuppressWarnings("unused")
    private void decResources(AbstractState e) {
      countResources--;
    }

    boolean isEmpty() {
      return mainWaitlist.isEmpty() && extraWaitlist.isEmpty();
    }

    boolean removeState(AbstractState e) {
      boolean b = mainWaitlist.remove(e) && extraWaitlist.remove(e);
      if(b) {
        //remove resources for e in block
        decResources(e);
      }
      return b;
    }

    AbstractState popState() {
      AbstractState res;
      if(!extraWaitlist.isEmpty()) {
        //first of all take state from extra
        //because it contains path continuations into depth
        res = extraWaitlist.pop();
        //remove resources for e
        decResources(res);
        return res;
      } else if(!mainWaitlist.isEmpty()) {
        res = mainWaitlist.pop();
        //remove resources for e
        decResources(res);
        return res;
      } else {
        assert false : "invalid pop: current block is empty";
        return null;
      }
    }
  }

  private final WaitlistFactory wrappedWaitlist;

  private int size = 0;
  //the map of active blocks (for efficient state removal)
  private Map<String,Block> activeBlocksMap;
  //the last element of the list of active blocks
  //current block
  private Block currBlock;
  //map of inactive blocks (where resource limits are reached)
  private Map<String,Block> inactiveBlocksMap;
  /**
   * Constructor that needs a factory for the waitlist implementation that
   * should be used to store states with the same block.
   */
  protected BlockWaitlist(WaitlistFactory pSecondaryStrategy) {
    wrappedWaitlist = Preconditions.checkNotNull(pSecondaryStrategy);
    activeBlocksMap = new HashMap<>();
    inactiveBlocksMap = new HashMap<>();
  }

  /**
   * add newBlock as the last element in the activeList
   * @param newBlock the block to be added
   */
  private void addNewBlock(Block newBlock) {
    newBlock.prev = currBlock;
    currBlock = newBlock;
    activeBlocksMap.put(newBlock.name, newBlock);
    size++;
  }

  /**
   * While last block is not empty
   * the other blocks should not be removed
   */
  private void removeLastBlock() {
    //remove currBlock from activeBlocksMap
    activeBlocksMap.remove(currBlock.name);
    //change the references prev, next
    //(remove currBlock from activeBlocks)
    currBlock = currBlock.prev;
    size--;
    //clear block references
    currBlock.prev = null;
  }

  /**
   * mark last active block as inactive
   */
  private void makeLastBlockInactive() {
    assert currBlock!=null;
    inactiveBlocksMap.put(currBlock.name, currBlock);
    //save resource count
    currBlock.savedResources = currBlock.countResources;
    removeLastBlock();
  }

  Pattern ldvPattern = Pattern.compile("ldv_.*_instance_.*");
  /**
   * checks whether function name is a block
   * (for example, starts with emg_control or emg_callback
   * or matches ldv_.*_instance_)
   * @return true if it is a block entry
   */
  private boolean isBlock(String func) {
    Matcher matcher = ldvPattern.matcher(func);
    return matcher.matches();
  }

  /**
   * @return function name for the block
   */
  private String getBlockFunc(AbstractState e) {
    CallstackState callStack = retreiveCallstack(e);
    while(callStack!=null) {
        //get current function
        String func = callStack.getCurrentFunction();
        if(isBlock(func)) {
          return func;
        }
        callStack = callStack.getPreviousState();
    }
    return null;
  }


  /**
   * get block for state e
   * @param e the state for which we need a block
   * @return block for state e
   */
  private Block getBlockForState(AbstractState e) {
    String func = getBlockFunc(e);
    if(func == null) {
      //not found
      return null;
    }
    //search block in active blocks
    Block block = activeBlocksMap.get(func);
    if(block != null) {
      return block;
    }

    //search block in inactive blocks
    block = inactiveBlocksMap.get(func);
    if(block != null) {
      return block;
    }
    return null;
  }

  @Override
  public Iterator<AbstractState> iterator() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void add(AbstractState pState) {
    CallstackState callStack = retreiveCallstack(pState);
    String func = callStack.getCurrentFunction();
    size++;

    if(currBlock == null) {
      //create entry block
        Block b = new Block(wrappedWaitlist);
        b.isEntryBlock = true;
        addNewBlock(b);
        b.addStateToMain(pState);
        return;
    }

    Block prevBlock = currBlock.prev;

    //three variants: current, previous, new
    if(prevBlock!=null) {
      //if exit from the function to previous block
      CallstackState prevCallstack = prevBlock.getLastCallstack();
      if(prevCallstack.equals(callStack)) {
        assert getBlockForState(pState) == prevBlock;
        //add return state to the previous block
        prevBlock.addStateToExtra(pState);
        //do not check resources here
        return;
      }
    }

    if(func.equals(currBlock.name)) {
      //state belongs to the current block
      currBlock.addStateToMain(pState);
      if(currBlock.checkResources()) {
          //stop analysis for the current block
          makeLastBlockInactive();
      }
    } else {
      if(isBlock(func)) {
        //check whether block is inactive
        if(inactiveBlocksMap.containsKey(func)) {
          //add state to inactive block
          //TODO: optimization - do not add
          Block block = inactiveBlocksMap.get(func);
          block.addStateToMain(pState);
        } else {
          //create new block
          Block newBlock = new Block(wrappedWaitlist);
          addNewBlock(newBlock);
          newBlock.addStateToMain(pState);
          //do not check resources
        }
      } else {
        //new state belongs to the same block
        currBlock.addStateToMain(pState);
        if(currBlock.checkResources()) {
          //stop analysis for the current block
          makeLastBlockInactive();
        }
      }
    }
  }

  @Override
  public boolean contains(AbstractState pState) {
    Block block = getBlockForState(pState);
    if(block == null) {
        return false;
    }
    return block.mainWaitlist.contains(pState)
        || block.extraWaitlist.contains(pState);
  }

  @Override
  public boolean remove(AbstractState pState) {
    //remove may be called even if the state is not in the waitlist
    Block block = getBlockForState(pState);
    if(block==null) {
      return false;
    }
    boolean b = block.removeState(pState);
    if(!b) {
      return false;
    }
    //if block becomes empty and it is the last,
    //then it it should be removed
    //together with all previous empty blocks
    if(block==currBlock) {
      while(currBlock!=null && currBlock.isEmpty()) {
        removeLastBlock();
      }
    }
    return true;
  }

  @Override
  public AbstractState pop() {
    assert !isEmpty() && !currBlock.isEmpty();
    AbstractState e = currBlock.popState();

    while(currBlock!=null && currBlock.isEmpty()) {
      removeLastBlock();
    }
    return e;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return currBlock == null;
  }

  @Override
  public void clear() {
    activeBlocksMap.clear();
    inactiveBlocksMap.clear();
    currBlock = null;
    size = 0;
  }

}
