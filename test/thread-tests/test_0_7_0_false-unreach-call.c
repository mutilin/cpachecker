#include "pthread_test.h"

pthread_mutex_t m;
int res = 0;

struct data_t
{
    int a;
    void (*func)();
} data;

int thread_data1 = 1;
int thread_data2 = 2;

pthread_t thread1;
pthread_t thread2;

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
    data.func();

	pthread_exit(0);
}

int main()
{
    int a = 1;
    if (a < 1)
        data.func = true_func;
	else
		data.func = false_func;

	pthread_create(&thread1, NULL, thread_func, (void *) &thread_data1);
	pthread_create(&thread2, NULL, thread_func, (void *) &thread_data2);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
