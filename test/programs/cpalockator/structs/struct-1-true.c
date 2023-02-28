// The test contains one struct and two function calls from pointers to different fields of this struct.

void pthread_create(int*, int, void * (void *arg), int);
void __VERIFIER_assume(int);

struct numbers {
  int num1;
  int num2;
};

struct numbers s1;

void f (int* a){
  *a = 1;
}

void *t1(void *arg) {
  f(&(s1.num1));
  return 0;
}

void *t2(void* arg) {
  f(&(s1.num2));
  return 0;
}

int main() {
  int thread1, thread2;
  
  pthread_create(&thread1, 0, t1, 0);
  pthread_create(&thread2, 0, t2, 0);
  
  return 0;
}
