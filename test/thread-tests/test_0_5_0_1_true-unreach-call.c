#include "pthread_test.h"

pthread_mutex_t m;
int res = 0;

struct thread_data_t
{
    int a;
    void (*func)();
};

void
true_func()
{
    ldv_mutex_model_lock(&m, NULL);
    res = res + 1;
    ldv_mutex_model_unlock(&m, NULL);
}

void
false_func()
{
    res = res + 1;
}

void *
thread_func(void *thread_data)
{
    struct thread_data_t *data = thread_data;
    data->func();

	pthread_exit(0);
}

int main()
{
	struct thread_data_t *thread_data;
    thread_data->func = true_func;

	pthread_t thread1;
	pthread_t thread2;

	pthread_create(&thread1, NULL, thread_func, (void *) thread_data);
	pthread_create(&thread2, NULL, thread_func, (void *) thread_data);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
