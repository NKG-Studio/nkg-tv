#pragma once
// Original Vulkan compute prototype. Uses the exact original TinySR2 weights.
// Host-visible buffers are a correctness bridge, NOT the final zero-copy player.
#include <vulkan/vulkan.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>
namespace nkg {
inline void check(VkResult r,const char* op){if(r!=VK_SUCCESS)throw std::runtime_error(std::string(op)+": VkResult="+std::to_string(r));}
class VulkanSr {
 VkInstance instance=VK_NULL_HANDLE;VkPhysicalDevice physical=VK_NULL_HANDLE;VkDevice device=VK_NULL_HANDLE;VkQueue queue=VK_NULL_HANDLE;
 VkDescriptorSetLayout descriptorLayout=VK_NULL_HANDLE;VkPipelineLayout layout=VK_NULL_HANDLE;VkShaderModule shader=VK_NULL_HANDLE;VkPipeline pipeline=VK_NULL_HANDLE;
 VkCommandPool commandPool=VK_NULL_HANDLE;VkCommandBuffer commands=VK_NULL_HANDLE;VkFence fence=VK_NULL_HANDLE;
 VkDescriptorPool descriptors=VK_NULL_HANDLE;VkDescriptorSet descriptor=VK_NULL_HANDLE;
 struct Buffer{VkBuffer buffer=VK_NULL_HANDLE;VkDeviceMemory memory=VK_NULL_HANDLE;void* mapped=nullptr;} input,output;
 uint32_t width=0,height=0;
 void releaseBuffer(Buffer& b){if(!device)return;if(b.mapped)vkUnmapMemory(device,b.memory);if(b.buffer)vkDestroyBuffer(device,b.buffer,nullptr);if(b.memory)vkFreeMemory(device,b.memory,nullptr);b={};}
 Buffer buffer(VkDeviceSize size){
  Buffer b;try{VkBufferCreateInfo info{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};info.size=size;info.usage=VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;info.sharingMode=VK_SHARING_MODE_EXCLUSIVE;check(vkCreateBuffer(device,&info,nullptr,&b.buffer),"create buffer");
   VkMemoryRequirements required{};vkGetBufferMemoryRequirements(device,b.buffer,&required);VkPhysicalDeviceMemoryProperties memory{};vkGetPhysicalDeviceMemoryProperties(physical,&memory);uint32_t type=memory.memoryTypeCount;
   for(uint32_t i=0;i<memory.memoryTypeCount;i++)if((required.memoryTypeBits&(1u<<i))&&(memory.memoryTypes[i].propertyFlags&(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))==(VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT|VK_MEMORY_PROPERTY_HOST_COHERENT_BIT)){type=i;break;}
   if(type==memory.memoryTypeCount)throw std::runtime_error("no coherent host-visible prototype buffer memory");
   VkMemoryAllocateInfo alloc{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};alloc.allocationSize=required.size;alloc.memoryTypeIndex=type;check(vkAllocateMemory(device,&alloc,nullptr,&b.memory),"allocate buffer");check(vkBindBufferMemory(device,b.buffer,b.memory,0),"bind buffer");check(vkMapMemory(device,b.memory,0,size,0,&b.mapped),"map buffer");return b;
  }catch(...){releaseBuffer(b);throw;}
 }
 public:
 VkPhysicalDeviceProperties properties{};
 VulkanSr(const std::vector<uint32_t>& spirv,bool allowSoftware){try{
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};app.pApplicationName="NKG GPU correctness prototype";app.apiVersion=VK_API_VERSION_1_0;
  VkInstanceCreateInfo ii{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};ii.pApplicationInfo=&app;check(vkCreateInstance(&ii,nullptr,&instance),"create instance");uint32_t count=0;check(vkEnumeratePhysicalDevices(instance,&count,nullptr),"enumerate count");if(!count)throw std::runtime_error("no Vulkan device");std::vector<VkPhysicalDevice> devices(count);check(vkEnumeratePhysicalDevices(instance,&count,devices.data()),"enumerate devices");
  // Prefer hardware; software is accepted only by explicit test opt-in.
  for(auto d:devices){VkPhysicalDeviceProperties p{};vkGetPhysicalDeviceProperties(d,&p);if(p.deviceType!=VK_PHYSICAL_DEVICE_TYPE_CPU){physical=d;properties=p;break;}}
  if(!physical&&allowSoftware){physical=devices[0];vkGetPhysicalDeviceProperties(physical,&properties);}if(!physical)throw std::runtime_error("software Vulkan cannot satisfy hardware requirement");
  uint32_t familyCount=0;vkGetPhysicalDeviceQueueFamilyProperties(physical,&familyCount,nullptr);std::vector<VkQueueFamilyProperties> families(familyCount);vkGetPhysicalDeviceQueueFamilyProperties(physical,&familyCount,families.data());uint32_t family=familyCount;
  for(uint32_t i=0;i<familyCount;i++)if(families[i].queueCount&&(families[i].queueFlags&VK_QUEUE_COMPUTE_BIT)){family=i;break;}if(family==familyCount)throw std::runtime_error("no compute queue");
  float priority=1;VkDeviceQueueCreateInfo qi{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};qi.queueFamilyIndex=family;qi.queueCount=1;qi.pQueuePriorities=&priority;VkDeviceCreateInfo di{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};di.queueCreateInfoCount=1;di.pQueueCreateInfos=&qi;check(vkCreateDevice(physical,&di,nullptr,&device),"create device");vkGetDeviceQueue(device,family,0,&queue);
  VkDescriptorSetLayoutBinding bindings[2]{};for(uint32_t i=0;i<2;i++){bindings[i].binding=i;bindings[i].descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;bindings[i].descriptorCount=1;bindings[i].stageFlags=VK_SHADER_STAGE_COMPUTE_BIT;}
  VkDescriptorSetLayoutCreateInfo dl{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};dl.bindingCount=2;dl.pBindings=bindings;check(vkCreateDescriptorSetLayout(device,&dl,nullptr,&descriptorLayout),"descriptor layout");
  VkPushConstantRange push{VK_SHADER_STAGE_COMPUTE_BIT,0,60};VkPipelineLayoutCreateInfo pl{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};pl.setLayoutCount=1;pl.pSetLayouts=&descriptorLayout;pl.pushConstantRangeCount=1;pl.pPushConstantRanges=&push;check(vkCreatePipelineLayout(device,&pl,nullptr,&layout),"pipeline layout");
  if(spirv.empty()||spirv[0]!=0x07230203)throw std::runtime_error("invalid SPIR-V");VkShaderModuleCreateInfo sm{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};sm.codeSize=spirv.size()*4;sm.pCode=spirv.data();check(vkCreateShaderModule(device,&sm,nullptr,&shader),"shader module");
  VkComputePipelineCreateInfo cp{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};cp.stage.sType=VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;cp.stage.stage=VK_SHADER_STAGE_COMPUTE_BIT;cp.stage.module=shader;cp.stage.pName="main";cp.layout=layout;check(vkCreateComputePipelines(device,VK_NULL_HANDLE,1,&cp,nullptr,&pipeline),"compute pipeline");
  VkCommandPoolCreateInfo pc{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};pc.queueFamilyIndex=family;pc.flags=VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;check(vkCreateCommandPool(device,&pc,nullptr,&commandPool),"command pool");VkCommandBufferAllocateInfo ca{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};ca.commandPool=commandPool;ca.level=VK_COMMAND_BUFFER_LEVEL_PRIMARY;ca.commandBufferCount=1;check(vkAllocateCommandBuffers(device,&ca,&commands),"command buffer");
  VkFenceCreateInfo fi{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};check(vkCreateFence(device,&fi,nullptr,&fence),"fence");
  VkDescriptorPoolSize size{VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,2};VkDescriptorPoolCreateInfo dp{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};dp.maxSets=1;dp.poolSizeCount=1;dp.pPoolSizes=&size;check(vkCreateDescriptorPool(device,&dp,nullptr,&descriptors),"descriptor pool");VkDescriptorSetAllocateInfo da{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};da.descriptorPool=descriptors;da.descriptorSetCount=1;da.pSetLayouts=&descriptorLayout;check(vkAllocateDescriptorSets(device,&da,&descriptor),"descriptor set");
 }catch(...){destroy();throw;}}
 VulkanSr(const VulkanSr&)=delete;VulkanSr& operator=(const VulkanSr&)=delete;
 ~VulkanSr(){destroy();}
 void destroy(){if(device){vkDeviceWaitIdle(device);releaseBuffer(input);releaseBuffer(output);if(descriptors)vkDestroyDescriptorPool(device,descriptors,nullptr);if(fence)vkDestroyFence(device,fence,nullptr);if(commandPool)vkDestroyCommandPool(device,commandPool,nullptr);if(pipeline)vkDestroyPipeline(device,pipeline,nullptr);if(shader)vkDestroyShaderModule(device,shader,nullptr);if(layout)vkDestroyPipelineLayout(device,layout,nullptr);if(descriptorLayout)vkDestroyDescriptorSetLayout(device,descriptorLayout,nullptr);vkDestroyDevice(device,nullptr);device=VK_NULL_HANDLE;}if(instance){vkDestroyInstance(instance,nullptr);instance=VK_NULL_HANDLE;}}
 void resize(uint32_t w,uint32_t h){
  if(!w||!h||w>4096||h>4096||uint64_t(w)*h>4096ull*2160)throw std::runtime_error("prototype source dimension budget exceeded; no downsampling fallback");
  uint64_t inBytes=uint64_t(w)*h*4,outBytes=inBytes*4;if(outBytes>properties.limits.maxStorageBufferRange)throw std::runtime_error("output exceeds device buffer range; no downsampling fallback");
  if(w==width&&h==height)return;check(vkDeviceWaitIdle(device),"resize wait");releaseBuffer(input);releaseBuffer(output);width=height=0;input=buffer(inBytes);output=buffer(outBytes);
  VkDescriptorBufferInfo buffers[2]={{input.buffer,0,inBytes},{output.buffer,0,outBytes}};VkWriteDescriptorSet writes[2]{};for(uint32_t i=0;i<2;i++){writes[i].sType=VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;writes[i].dstSet=descriptor;writes[i].dstBinding=i;writes[i].descriptorCount=1;writes[i].descriptorType=VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;writes[i].pBufferInfo=&buffers[i];}vkUpdateDescriptorSets(device,2,writes,0,nullptr);width=w;height=h;
 }
 struct Plane{const uint8_t* data;size_t bytes;uint32_t row,pixel;};
 struct Shape{uint32_t width,height,mode,bt709,fullRange,uOffset,vOffset,yRow,uRow,vRow,yPixel,uPixel,vPixel,cropX,cropY;};
 void infer(const uint32_t* source,uint32_t* destination,uint32_t w,uint32_t h){
  if(!source||!destination)throw std::runtime_error("null pixel buffer");resize(w,h);std::memcpy(input.mapped,source,size_t(w)*h*4);Shape shape{};shape.width=w;shape.height=h;dispatch(shape,destination);
 }
 void inferYuv(const Plane* planes,uint32_t* destination,uint32_t w,uint32_t h,uint32_t cropX,uint32_t cropY,bool bt709,bool fullRange){
  if(!planes||!destination||cropX>32768||cropY>32768)throw std::runtime_error("invalid YUV input");resize(w,h);size_t offsets[3]={};size_t total=0;
  for(int i=0;i<3;i++){const Plane& p=planes[i];if(!p.data||!p.row||!p.pixel)throw std::runtime_error("invalid YUV plane");uint64_t x=cropX+uint64_t(w)-1,y=cropY+uint64_t(h)-1;if(i){x/=2;y/=2;}uint64_t last=y*p.row+x*p.pixel;
   if(last>=p.bytes||p.bytes>uint64_t(w)*h*4)throw std::runtime_error("YUV plane is truncated or exceeds source budget");offsets[i]=total;total+=(p.bytes+3)&~size_t(3);}
  if(total>uint64_t(w)*h*4)throw std::runtime_error("YUV stride budget exceeded; no resolution reduction");
  std::memset(input.mapped,0,total);for(int i=0;i<3;i++)std::memcpy(static_cast<uint8_t*>(input.mapped)+offsets[i],planes[i].data,planes[i].bytes);
  Shape shape{w,h,1,bt709?1u:0u,fullRange?1u:0u,uint32_t(offsets[1]),uint32_t(offsets[2]),planes[0].row,planes[1].row,planes[2].row,planes[0].pixel,planes[1].pixel,planes[2].pixel,cropX,cropY};dispatch(shape,destination);
 }
 private:
 void dispatch(const Shape& shape,uint32_t* destination){uint32_t w=shape.width,h=shape.height;check(vkResetFences(device,1,&fence),"reset fence");check(vkResetCommandBuffer(commands,0),"reset commands");VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};begin.flags=VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;check(vkBeginCommandBuffer(commands,&begin),"begin commands");
  VkMemoryBarrier ready{VK_STRUCTURE_TYPE_MEMORY_BARRIER};ready.srcAccessMask=VK_ACCESS_HOST_WRITE_BIT;ready.dstAccessMask=VK_ACCESS_SHADER_READ_BIT;vkCmdPipelineBarrier(commands,VK_PIPELINE_STAGE_HOST_BIT,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,0,1,&ready,0,nullptr,0,nullptr);
  vkCmdBindPipeline(commands,VK_PIPELINE_BIND_POINT_COMPUTE,pipeline);vkCmdBindDescriptorSets(commands,VK_PIPELINE_BIND_POINT_COMPUTE,layout,0,1,&descriptor,0,nullptr);vkCmdPushConstants(commands,layout,VK_SHADER_STAGE_COMPUTE_BIT,0,sizeof(shape),&shape);vkCmdDispatch(commands,(w+7)/8,(h+7)/8,1);
  VkMemoryBarrier done{VK_STRUCTURE_TYPE_MEMORY_BARRIER};done.srcAccessMask=VK_ACCESS_SHADER_WRITE_BIT;done.dstAccessMask=VK_ACCESS_HOST_READ_BIT;vkCmdPipelineBarrier(commands,VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,VK_PIPELINE_STAGE_HOST_BIT,0,1,&done,0,nullptr,0,nullptr);check(vkEndCommandBuffer(commands),"end commands");VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};submit.commandBufferCount=1;submit.pCommandBuffers=&commands;check(vkQueueSubmit(queue,1,&submit,fence),"submit");check(vkWaitForFences(device,1,&fence,VK_TRUE,10'000'000'000ull),"compute completion timeout");std::memcpy(destination,output.mapped,size_t(w)*h*16);
 }
};
}
