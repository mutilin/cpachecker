#include "pthread_test.h"

pthread_mutex_t m;
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
thread_func(void (*func1)(void))
{
    func1();
	pthread_exit(0);
}

int main()
{
	void *thread_data1 = NULL;
	void *thread_data2 = NULL;
	pthread_t thread1;
	pthread_t thread2;

    int a = 1;
    if (a < 1)
		func = true_func;
	else
		func = false_func;

	pthread_create(&thread1, NULL, thread_func, (void *) func);
	pthread_create(&thread2, NULL, thread_func, (void *) func);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
