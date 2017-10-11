#include "pthread_test.h"

int res = 0;

void *
err_thread_func(void *thread_data)
{
    res = res + 1;
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

	pthread_create(&thread1, NULL, err_thread_func, thread_data1);
	pthread_create(&thread2, NULL, err_thread_func, thread_data2);

	pthread_join(thread1, NULL);
	pthread_join(thread2, NULL);

    int out = res;

	return 0;
}
