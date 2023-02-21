// The test checks deadlock on different locks

typedef int pthread_mutex_t;
typedef unsigned long int pthread_t;
typedef int pthread_attr_t;
extern void pthread_mutex_lock(pthread_mutex_t *lock) ;
extern void pthread_mutex_unlock(pthread_mutex_t *lock) ;
extern void ldv_spin_model_lock(int *lock) ;
extern void ldv_spin_model_unlock(int *lock) ;
extern int pthread_create(pthread_t *thread_id , pthread_attr_t const   *attr , void *(*func)(void * ) ,
                          void *arg ) ;


int global;
pthread_mutex_t mutex;
int spin_lock;

int f(void *arg) {
	pthread_mutex_lock(&mutex);
	ldv_spin_model_lock(&spin_lock);
    global = 1;
	ldv_spin_model_unlock(&spin_lock);
	pthread_mutex_unlock(&mutex);
}

int g(void *arg) {
	ldv_spin_model_lock(&spin_lock);
	pthread_mutex_lock(&mutex);
    global = 2;
	pthread_mutex_unlock(&mutex);
	ldv_spin_model_unlock(&spin_lock);
}

int main() {
    pthread_t thread;
    pthread_t thread2;
	pthread_create(&thread, 0, &f, 0);
	pthread_create(&thread2, 0, &g, 0);
}
