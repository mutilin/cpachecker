#define __PTHREAD_SIZE__ 8176
#define __PTHREAD_MUTEX_SIZE__ 56
#define __PTHREAD_MUTEXATTR_SIZE__	8
#define NULL 0

struct __darwin_pthread_handler_rec
{
	void (*__routine)(void *);
	void *__arg;
	struct __darwin_pthread_handler_rec *__next;
};

typedef struct pthread_t
{
	long __sig;
	struct __darwin_pthread_handler_rec  *__cleanup_stack;
	char __opaque[__PTHREAD_SIZE__];
} pthread_t;

typedef struct pthread_attr_t
{
	long __sig;
	char __opaque[__PTHREAD_ATTR_SIZE__];
} pthread_attr_t;

typedef struct pthread_mutex_t
{
	long __sig;
	char __opaque[__PTHREAD_MUTEX_SIZE__];
} pthread_mutex_t;

typedef struct pthread_mutexattr_t
{
	long __sig;
	char __opaque[__PTHREAD_MUTEXATTR_SIZE__];
} pthread_mutexattr_t;

void pthread_exit(void *data);
int pthread_create(pthread_t *pthread, const pthread_attr_t *attr,
                   void *(*func)(void *), void *data);
int pthread_join(pthread_t pthread, void *data);
int pthread_mutex_init(pthread_mutex_t *pthread,
                       const pthread_mutexattr_t *attr);
int pthread_mutex_lock(pthread_mutex_t *m);
int pthread_mutex_unlock(pthread_mutex_t *m);

pthread_mutex_t m;
int res;

void *
err_thread_func(void *thread_data)
{
    pthread_mutex_lock(&m);
    pthread_mutex_unlock(&m);
    res = (int) *thread_data;
	pthread_exit(0);
}

int main()
{
    int n1 = 1;
    int n2 = 2;
	void *thread_data1 = (void *)&n1;
	void *thread_data2 = (void *)&n2;
	pthread_t thread1;
	pthread_t thread2;

    pthread_mutex_init(&m, NULL);

	pthread_create(&thread1, NULL, err_thread_func, thread_data1);
	pthread_create(&thread2, NULL, err_thread_func, thread_data2);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
