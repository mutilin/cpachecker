// The test accesses two variables using the same pointer
// There is a data race between assignments to pointer "global_ptr" (line 15 and line 21). There can also be a data race between assignment to pointer and function call (l.15 and l.22; or l.16 and l.21)

void pthread_create(int*, int, void * (void *arg), int);
void __VERIFIER_assume(int);

int global1 = 0, global2 = 0;
int* global_ptr;

void f (int* a){
  *a = 1;
}

void *t1(void *arg) {
  global_ptr = &global1;
  f(global_ptr);
  return 0;
}

void *t2(void* arg) {
  global_ptr = &global2;
  f(global_ptr);
  return 0;
}

int main() {
  int thread1, thread2;
  
  pthread_create(&thread1, 0, t1, 0);
  pthread_create(&thread2, 0, t2, 0);
  
  return 0;
}
