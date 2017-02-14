void
func0(void)
{
    return;
}

void
func1(void)
{
    ERROR: goto ERROR;
}

struct str
{
    int a;
    void (*fptr)(void);
};

int
main(int argc, char **argv)
{
    struct str *st;
    st->fptr = func0;
    st->fptr();

    return 0;
}

