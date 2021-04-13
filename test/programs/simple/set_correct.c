struct mutex;

extern void mutex_init(struct mutex *lock);
extern void mutex_lock(struct mutex *lock);
extern void mutex_unlock(struct mutex *lock);
void check_final_state(void);
void error(void);

void f(struct mutex *lock)
{
  mutex_lock(lock);
}

void main(void)
{
  struct mutex mutex_1, mutex_2;
  //mutex_1 = 1;
  //mutex_2 = 2;

  int i = 0;
  mutex_init(&mutex_1);
  //mutex_init(&mutex_2);

  mutex_lock(&mutex_1);
  //mutex_lock(&mutex_1);
  //mutex_lock(&mutex_2); // no double lock
  if (i == 0){
    //mutex_lock(&mutex_1);
    //mutex_lock(&mutex_2); // no double lock
    //mutex_unlock(&mutex_2);
    mutex_unlock(&mutex_1);
  }
  //if (i==2){
  //      error();
  //}

  //int j;
  //for (j=0;j<10;j++){
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
  //mutex_lock(&mutex_1);

  check_final_state();

  //for (j=0;j<5;j++);
}