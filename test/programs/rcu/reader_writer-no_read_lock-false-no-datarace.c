int pthread_create(int * thread, int * attr, void*(*start)(void*), void *);

void ldv_rcu_read_lock(void) {

}

void ldv_rcu_read_unlock(void) {

}

void ldv_rlock_rcu(void) {

}

void ldv_runlock_rcu(void) {

}

void * ldv_rcu_dereference(void * pp) {

}

void ldv_wlock_rcu(void) {

}

void ldv_wunlock_rcu(void) {

}

void ldv_free(void *) {

}

void ldv_synchronize_rcu(void) {

}

void ldv_rcu_assign_pointer(void * p1, void * p2) {

}

char * gp;

int reader(void * arg) {
    char *a;
    char b;
    char * p = &b;

    //ldv_rcu_read_lock();//BUG is here! No read lock
    char * p__1;
    ldv_rlock_rcu();
    p__1 = ldv_rcu_dereference(gp);
    ldv_runlock_rcu();
    a = p__1;
    b = *a;
    //ldv_rcu_read_unlock();
    
    return 0;
}

int writer(void * arg) {
  char * p = calloc(3 * sizeof(int));
  char * ptr;
                      
  p[1] = 'd';

  do {
    ldv_wlock_rcu();
    ldv_rcu_assign_pointer(gp, p);
    ldv_wunlock_rcu();
  } while(0);
  ldv_synchronize_rcu();
  ldv_free(ptr);

  return 0;
}

int main() {

  gp = calloc(3 * sizeof(int));

  int rd, wd;
  pthread_create(&rd, 0, reader, 0);
  pthread_create(&wd, 0, writer, 0);

  return rd + wd;
}
