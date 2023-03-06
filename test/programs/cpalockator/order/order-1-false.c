// The test contains two threads. Assertion can be reached by successively applying two operations from thread2 to thread1.

extern void __VERIFIER_error() __attribute__ ((__noreturn__));
int __VERIFIER_nondet_int(void);
void assert(int expression) { if (!expression) { ERROR: __VERIFIER_error();}; return; }

int a = 0, b = 0, c = 0;

void *thread1(void *arg) {
    if (a == 1){
    	assert(b != 0);
    }
}

void *thread2(void *arg) {
    c = 10;
    a = c - 9;
}

int main(void) {
    int t1, t2;
    pthread_create(&t1, 0, thread1, 0);
    pthread_create(&t2, 0, thread2, 0);
    return 0;
}
