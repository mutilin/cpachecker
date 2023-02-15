// The test contains two function calls from addresses of different variables 

void pthread_create(int*, int, void * (void *arg), int);
void __VERIFIER_assume(int);

int global1 = 0, global2 = 0;

void f (int* a){
  *a = 1;
}

void *t1(void *arg) {
  f(&global1);
  return 0;
}

void *t2(void* arg) {
  f(&global2);
  return 0;
}

int main() {
  int thread1, thread2;
  
  pthread_create(&thread1, 0, t1, 0);
  pthread_create(&thread2, 0, t2, 0);
  
  return 0;
}
