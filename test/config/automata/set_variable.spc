// This automaton checks correct usage of mutex locks (simplified version):
// it is forbidden to acquire or release the same mutex twice in the same process and
// all acquired mutexes should be released at finalization.
// In order to differentiate mutexes a set automata variable is used.

CONTROL AUTOMATON set_automaton_variable

// Declare automaton variable 'mtxs' of type set with string element type.
//LOCAL set<int> mtxs = [];

INITIAL STATE Init;

STATE USEALL Init :
  // Init mutex
  //MATCH {mutex_lock($1)} -> MODIFY(Predicate, "setcontains(mtxs, $1)") ERROR("mutex_lock:double lock");
  MATCH {mutex_init($1)} -> MODIFY(Predicate, "setinit(mtxs, $1)") GOTO Init;

  // Check if this mutex was not acquired twice (element '$1' is not contained in the 'mtxs' set).
  //MATCH {mutex_lock($1)} -> MODIFY(Predicate, "!setcontains(mtxs, $1); elseerror") GOTO Init;
  //MATCH {mutex_lock($1)} -> ASSERT CHECK(Predicate, "!setcontains(mtxs, $1)") GOTO Init;
  // Acquire this mutex (add element '$1' to the 'mtxs' set).
  MATCH {mutex_lock($1)} -> MODIFY(Predicate, "setadd(mtxs, $1)") GOTO Init;

  // Check if this mutex was acquired before (element '$1' is contained in the 'mtxs' set).
  //MATCH {mutex_unlock($1)} -> MODIFY(Predicate, "!setcontains(mtxs, $1)") ERROR("mutex_lock:double unlock");
  //MATCH {mutex_unlock($1)} -> MODIFY(Predicate, "setcontains(mtxs, $1); elseerror") GOTO Init;
  //MATCH {mutex_unlock($1)} -> ASSERT CHECK(Predicate, "setcontains(mtxs, $1)") GOTO Init;
  // Release this mutex (remove element '$1' from the 'mtxs' set).
  MATCH {mutex_unlock($1)} -> MODIFY(Predicate, "setremove(mtxs, $1)") GOTO Init;

  // Check that all mutexes were released at finalization (the 'mtxs' set is empty).
  //MATCH {check_final_state($?)} -> MODIFY(Predicate, "!setempty(mtxs)") ERROR("mutex_lock:locked at exit");
  MATCH {check_final_state($?)} -> MODIFY(Predicate, "setempty(mtxs); elseerror") GOTO Init;
  //MATCH {check_final_state($?)} -> ASSERT CHECK(Predicate, "setempty(mtxs)") GOTO Init;

  MATCH {error($?)} -> ERROR("OMG");

END AUTOMATON

