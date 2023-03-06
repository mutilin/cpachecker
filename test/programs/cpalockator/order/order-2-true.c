// The test contains two threads. Assertion can be falsely reached by applying two operations from thread2 to thread1 in reverse order.

extern void __VERIFIER_error() __attribute__ ((__noreturn__));
int __VERIFIER_nondet_int(void);
void assert(int expression) { if (!expression) { ERROR: __VERIFIER_error();}; return; }

int a = 0, b = 0;

void *thread1(void *arg) {
    if (a == 1){
      if (a == 2){
    	assert(b != 0);
      }
    }
}

void *thread2(void *arg) {
    a = 2;
    a = 1;
}

int main(void) {
    int t1, t2;
    pthread_create(&t1, 0, thread1, 0);
    pthread_create(&t2, 0, thread2, 0);
    return 0;
}
