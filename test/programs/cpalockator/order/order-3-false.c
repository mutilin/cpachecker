// The test contains three threads. Assertion can be reached by applying operation "a = c" from thread3 to thread1 before "b = 1" and after "a = 1".

extern void __VERIFIER_error() __attribute__ ((__noreturn__));
int __VERIFIER_nondet_int(void);
void assert(int expression) { if (!expression) { ERROR: __VERIFIER_error();}; return; }

int a = 0, b = 0, c = 0;

void *thread1(void *arg) {
    a = 1;
    b = 1;
    c = 1;
}

void *thread2(void *arg) {
    if (b != 0){
      assert (a != 0);
    }
}

void *thread3 (void *arg) {
    a = c;
}

int main(void) {
    int t1, t2, t3;
    pthread_create(&t1, 0, thread1, 0);
    pthread_create(&t2, 0, thread2, 0);
    pthread_create(&t3, 0, thread3, 0);
    return 0;
}
