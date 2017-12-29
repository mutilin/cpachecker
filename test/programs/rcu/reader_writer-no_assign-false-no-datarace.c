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
    char * pReader = &b;

    ldv_rcu_read_lock();
    char * p;
    ldv_rlock_rcu();
    p = ldv_rcu_dereference(gp);
    ldv_runlock_rcu();
    a = p;
    b = *a;
    ldv_rcu_read_unlock();
    
    return 0;
}

int writer(void * arg) {
  char * pWriter = calloc(3 * sizeof(int));
  char * ptr = gp;
                      
  pWriter[0] = 'r';
  pWriter[1] = 'c';
  pWriter[2] = 'u';

  gp=pWriter; //BUG! No rcu_assign_pointer.

  ldv_synchronize_rcu();
  ldv_free(ptr);

  return 0;
}

int main() {

  gp = calloc(3 * sizeof(int));

  int rd, wd;
  pthread_create(&rd, 0, reader, 0);
  pthread_create(&wd, 0, writer, 0);

  return 0;
}
