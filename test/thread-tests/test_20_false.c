void true_func()
{
}

void err_func()
{
	ERROR: goto ERROR;
}

int g(void (*fn)(void))
{
    fn();
}

int
main(int argc, char **argv)
{
    int a = 1;
    void (*func_var)(void);
    if (a < 1) {
		func_var = &true_func;
	} else {
		func_var = &err_func;
	}
    g(func_var);
    return 0;
}
