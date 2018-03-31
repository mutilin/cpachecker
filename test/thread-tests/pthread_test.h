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

void pthread_exit(void *data) {}
int pthread_create(pthread_t *pthread, const pthread_attr_t *attr,
                   void *(*func)(void *), void *data) {}
int pthread_join(pthread_t pthread, void *data) {}
int pthread_mutex_init(pthread_mutex_t *pthread,
                       const pthread_mutexattr_t *attr) {}
int pthread_mutex_lock(pthread_mutex_t *m) {}
int pthread_mutex_unlock(pthread_mutex_t *m) {}

int ldv_mutex_model_init(pthread_mutex_t *pthread,
                         const pthread_mutexattr_t *attr) {}
int ldv_mutex_model_lock(pthread_mutex_t *m, void *n) {}
int ldv_mutex_model_unlock(pthread_mutex_t *m, void *n) {}

