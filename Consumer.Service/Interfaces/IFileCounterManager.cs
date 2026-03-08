namespace Consumer.Service.Interfaces;

public interface IFileCounterManager
{
    int GetNextCounter();
    void IncrementCounter();
}
