struct str
{
    int a;
    void (*fptr)(void);
};

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
    struct str *st = thread_data;
    st->fptr();
	pthread_exit(0);
}

int main()
{
    struct str *thread_data;
    int a = 1;

    if (a < 1)
        thread_data->fptr = true_func;
    else
        thread_data->fptr = err_func;

	pthread_t thread;

	pthread_create(&thread, NULL, thread_func, (void *) thread_data);

	pthread_join(thread, NULL);

	return 0;
}
