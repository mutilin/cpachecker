#include "pthread_test.h"

pthread_mutex_t m;
int res = 0;

struct data_t
{
    int b;
    void (*func)();
};

struct thread_data_t
{
    int a;
    struct data_t data;
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
    struct thread_data_t *d0 = (struct thread_data_t *) thread_data; 
    struct thread_data_t data0 = *d0;
    data0.data.func();

	pthread_exit(0);
}

int main()
{
    int a = 1;
	struct thread_data_t thread_data;
    if (a < 1)
        thread_data.data.func = true_func;
	else
		thread_data.data.func = false_func;

	pthread_t thread1;
	pthread_t thread2;

	pthread_create(&thread1, NULL, thread_func, (void *) &thread_data);
	pthread_create(&thread2, NULL, thread_func, (void *) &thread_data);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
