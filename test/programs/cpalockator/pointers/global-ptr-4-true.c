// The test contains two double pointers to two different variables

void pthread_create(int*, int, void * (void *arg), int);
void __VERIFIER_assume(int);

int global1 = 0, global2 = 0;
int* global1_ptr, * global2_ptr;
int** global1_ptr_ptr, global2_ptr_ptr;

void f (int* a){
  *a = 1;
}

void *t1(void *arg) {
  f(*global1_ptr_ptr);
  return 0;
}

void *t2(void* arg) {
  f(*global2_ptr_ptr);
  return 0;
}

int main() {
  int thread1, thread2;
  
  global1_ptr = &global1;
  global1_ptr_ptr = &global1_ptr;
  global2_ptr = &global2;
  global2_ptr_ptr = &global2_ptr;
  
  pthread_create(&thread1, 0, t1, 0);
  pthread_create(&thread2, 0, t2, 0);
  
  return 0;
}
