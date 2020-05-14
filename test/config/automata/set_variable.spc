// This automaton checks correct usage of mutex locks (simplified version):
// it is forbidden to acquire or release the same mutex twice in the same process and
// all acquired mutexes should be released at finalization.
// In order to differentiate mutexes a set automata variable is used.

CONTROL AUTOMATON set_automaton_variable

// Declare automaton variable 'acquired_mutexes' of type set with string element type.
//LOCAL set<int> acquired_mutexes = [];

INITIAL STATE Init;

STATE USEALL Init :
  // Init mutex
  //MATCH {mutex_lock($1)} -> MODIFY(Predicate, "setcontains(acquired_mutexes, $1)") ERROR("mutex_lock:double lock");
  MATCH {mutex_init($1)} -> MODIFY(Predicate, "!setcontains(*acquired_mutexes, $1)") GOTO Init;

  // Check if this mutex was not acquired twice (element '$1' is not contained in the 'acquired_mutexes' set).
  MATCH {mutex_lock($1)} -> MODIFY(Predicate, "!setcontains(*acquired_mutexes, $1); elseerror") GOTO Init;
  // Acquire this mutex (add element '$1' to the 'acquired_mutexes' set).
  MATCH {mutex_lock($1)} -> MODIFY(Predicate, "setadd(*acquired_mutexes, $1)") GOTO Init;

  // Check if this mutex was acquired before (element '$1' is contained in the 'acquired_mutexes' set).
  //MATCH {mutex_unlock($1)} -> MODIFY(Predicate, "!setcontains(acquired_mutexes, $1)") ERROR("mutex_lock:double unlock");
  MATCH {mutex_unlock($1)} -> MODIFY(Predicate, "setcontains(*acquired_mutexes, $1); elseerror") GOTO Init;
  // Release this mutex (remove element '$1' from the 'acquired_mutexes' set).
  MATCH {mutex_unlock($1)} -> MODIFY(Predicate, "setremove(*acquired_mutexes, $1)") GOTO Init;

  // Check that all mutexes were released at finalization (the 'acquired_mutexes' set is empty).
  //MATCH {check_final_state($?)} -> MODIFY(Predicate, "!setempty(acquired_mutexes)") ERROR("mutex_lock:locked at exit");
  MATCH {check_final_state($?)} -> MODIFY(Predicate, "setempty(*acquired_mutexes); elseerror") GOTO Init;

END AUTOMATON

