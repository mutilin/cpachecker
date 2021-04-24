struct mutex;

extern void mutex_init(struct mutex *lock);
extern void mutex_lock(struct mutex *lock);
extern void mutex_unlock(struct mutex *lock);
void check_final_state(void);
void error(void);
extern void __VERIFIER_assume(int);
extern int __VERIFIER_nondet_int(void);

//void main() {
//    int data = 0;
//    while (__VERIFIER_nondet_int()) {
//        data = __VERIFIER_nondet_int();
//    }
//    if (data) {
//        ERROR: goto ERROR;
//    }
//}

void set_init()
{
  __VERIFIER_set_init();
}

void init_lock(struct lock *p)
{
  int c;
  c = __VERIFIER_check_locked(p);
  __VERIFIER_assume(!c);
}

void lock(struct lock *p)
{
  if (__VERIFIER_check_locked(p))
    __VERIFIER_error();
  __VERIFIER_set_locked(p);
}

void unlock(struct lock *p)
{
  if (!__VERIFIER_check_locked(p))
    __VERIFIER_error();
  __VERIFIER_set_unlocked(p);
}

void check_state()
{
  if(!__VERIFIER_check_empty())
    __VERIFIER_error();
}

void f(struct mutex *lock)
{
  unlock(lock);
}

void main(void)
{
  set_init();
  struct mutex mutex_1, mutex_2;
  //mutex_1 = 1;
  //mutex_2 = 2;

  int i = 0;
  init_lock(&mutex_1);
  //mutex_init(&mutex_1);
  //mutex_init(&mutex_2);

  //lock(&mutex_1);
  //f(&mutex_1);
  //unlock(&mutex_1);

  //lock(&mutex_1);
  //unlock(&mutex_1);

  //lock(&mutex_1);
  //unlock(&mutex_1);
  //mutex_lock(&mutex_1);
  //mutex_lock(&mutex_1);
  //mutex_lock(&mutex_2); // no double lock
  //if (i == 0){
    //unlock(&mutex_1);
    //mutex_lock(&mutex_1);
    //mutex_lock(&mutex_2); // no double lock
    //mutex_unlock(&mutex_2);
    //mutex_unlock(&mutex_1);
  //}
  //if (i==2){
  //      error();
  //}
  //lock(&mutex_1);

  //int j;
  //for (j=0;j<__VERIFIER_nondet_int();j++){
    //if (i == 0){
    //lock(&mutex_1);
    //}
    //unlock(&mutex_1);
    //lock(&mutex_1);
    //unlock(&mutex_1);
    //if (i==1 || j>15){
      //error();
      //check_final_state();
      //mutex_lock(&mutex_1);
    //}
    // mutex_lock(mutex_1);
    // mutex_lock(mutex_2); // no double lock
    // mutex_unlock(mutex_2);
    // mutex_unlock(mutex_1);
    //
    // //if (i == 0){
    // //mutex_lock(mutex_1);
    // //}//
    //check_final_state();
  //}
  //lock(&mutex_1);
  //mutex_lock(&mutex_1);

  lock(&mutex_1);
  while (1) {
    if (__VERIFIER_nondet_int())
      goto L1;
  }
L1:
  unlock(&mutex_1);

  //lock(&mutex_1);
  //  while (1) {
  //    if (__VERIFIER_nondet_int())
  //      goto L2;
  //  }
  //L2:
  //  unlock(&mutex_1);

  //check_final_state();
  check_state();

  //for (j=0;j<5;j++);
}