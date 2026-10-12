// Original diagnostic only; reports the device actually exposed by the Android driver.
#include <vulkan/vulkan.h>
#include <cstdio>
#include <vector>
int main(){
 VkApplicationInfo app{};app.sType=VK_STRUCTURE_TYPE_APPLICATION_INFO;app.pApplicationName="NKG capability probe";app.apiVersion=VK_API_VERSION_1_0;
 VkInstanceCreateInfo info{};info.sType=VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;info.pApplicationInfo=&app;VkInstance instance=VK_NULL_HANDLE;VkResult result=vkCreateInstance(&info,nullptr,&instance);
 std::printf("vkCreateInstance=%d\n",result);if(result!=VK_SUCCESS)return 2;
 uint32_t count=0;result=vkEnumeratePhysicalDevices(instance,&count,nullptr);std::printf("enumerate=%d count=%u\n",result,count);if(result!=VK_SUCCESS){vkDestroyInstance(instance,nullptr);return 3;}
 std::vector<VkPhysicalDevice> devices(count);vkEnumeratePhysicalDevices(instance,&count,devices.data());for(auto device:devices){VkPhysicalDeviceProperties p{};vkGetPhysicalDeviceProperties(device,&p);std::printf("device=%s type=%u vendor=%u api=%u driver=%u\n",p.deviceName,p.deviceType,p.vendorID,p.apiVersion,p.driverVersion);}
 vkDestroyInstance(instance,nullptr);return 0;
}
