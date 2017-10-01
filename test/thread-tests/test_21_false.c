#include <pthread.h>

void true_func()
{
}

void err_func()
{
	ERROR: goto ERROR;
}

void *
thread_func(void *thread_data)
{
    err_func();
	pthread_exit(0);
}

int main()
{

	void *thread_data = NULL;
	pthread_t thread;

	pthread_create(&thread, NULL, thread_func, thread_data);

	pthread_join(thread, NULL);

	return 0;
}
