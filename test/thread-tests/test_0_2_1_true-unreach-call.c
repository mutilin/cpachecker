#include "pthread_test.h"

pthread_mutex_t m;
pthread_mutex_t m2;
int res = 0;

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

void (*func)();

void *
thread_func(void *thread_data)
{
    ldv_mutex_model_lock(&m2, NULL);
    int a = 0;
    if (a < 1)
		func = true_func;
	else
		func = false_func;

    func();
    ldv_mutex_model_unlock(&m2, NULL);
	pthread_exit(0);
}

int main()
{
	void *thread_data1 = NULL;
	void *thread_data2 = NULL;
	pthread_t thread1;
	pthread_t thread2;

	pthread_create(&thread1, NULL, thread_func, thread_data1);
	pthread_create(&thread2, NULL, thread_func, thread_data2);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
