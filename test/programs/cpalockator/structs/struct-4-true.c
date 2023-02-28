// The test contains two pointers to differect structs and two function calls from these structs.

void pthread_create(int*, int, void * (void *arg), int);
void __VERIFIER_assume(int);

struct numbers {
  int num1;
  int num2;
};

struct numbers * s1, * s2;

void f (struct numbers * s){
  s->num1 = 1;
}

void *t1(void *arg) {
  f(s1);
  return 0;
}

void *t2(void* arg) {
  f(s2);
  return 0;
}

int main() {
  int thread1, thread2;
  
  s1 = malloc(sizeof(struct numbers));
  s2 = malloc(sizeof(struct numbers));
  pthread_create(&thread1, 0, t1, 0);
  pthread_create(&thread2, 0, t2, 0);
  
  return 0;
}
