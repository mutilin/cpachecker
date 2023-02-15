// The test contains two function calls from different pointers that point to different variables

void pthread_create(int*, int, void * (void *arg), int);
void __VERIFIER_assume(int);

int global1 = 0, global2 = 0;
int* global1_ptr, * global2_ptr;

void f (int* a){
  *a = 1;
}

void *t1(void *arg) {
  global1_ptr = &global1;
  f(global1_ptr);
  return 0;
}

void *t2(void* arg) {
  global2_ptr = &global2;
  f(global2_ptr);
  return 0;
}

int main() {
  int thread1, thread2;
  
  pthread_create(&thread1, 0, t1, 0);
  pthread_create(&thread2, 0, t2, 0);
  
  return 0;
}
